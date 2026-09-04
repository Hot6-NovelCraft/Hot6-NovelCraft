package com.example.hot6novelcraft.domain.payment.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import com.example.hot6novelcraft.domain.notification.producer.NotificationProducer;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentHistoryResponse;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentPrepareResponse;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentResponse;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentMethod;
import com.example.hot6novelcraft.domain.payment.repository.PaymentRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 결제 흐름 오케스트레이터.
 * DB 트랜잭션({@link PaymentTransactionService})과 외부 API 호출을 분리하여
 * 트랜잭션 점유 중 외부 API 대기가 발생하지 않도록 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionService paymentTransactionService;
    private final PaymentRepository paymentRepository;
    private final PaymentClient paymentClient;
    private final PointService pointService;
    private final RedisUtil redisUtil;
    private final NotificationProducer notificationProducer;

    /**
     * 내 결제 내역 목록 조회 (페이징, 최신순)
     */
    @Transactional(readOnly = true)
    public PageResponse<PaymentHistoryResponse> getPaymentHistory(Long userId, Pageable pageable) {
        Page<PaymentHistoryResponse> page = paymentRepository
                .findByUserId(userId, pageable)
                .map(PaymentHistoryResponse::from);
        return PageResponse.register(page);
    }

    /**
     * 결제창 열기 전 PENDING Payment 미리 생성
     */
    public PaymentPrepareResponse preparePayment(Long userId, Long amount) {
        log.info("[결제 준비] 요청 userId={} amount={}", userId, amount);
        return paymentTransactionService.preparePendingPayment(userId, amount);
    }

    /**
     * 포트원 V2 결제 확인 및 포인트 충전
     *
     * 흐름:
     * 1. [Redis Lock] paymentKey 단위로 락 획득 — /confirm과 웹훅 보정의 상호 배제 보장
     * 2. [TX] /prepare로 생성된 PENDING Payment 재사용
     * 3. [외부 API] 포트원 V2 SDK로 실제 결제 정보 조회 및 금액·상태 검증
     * 4. [TX] 검증 통과 시 COMPLETED 전환 + 포인트 충전 + 구매 이력 저장
     */
    public PaymentResponse confirmPayment(Long userId, PaymentConfirmRequest request) {
        log.info("[결제] 확인 시작 userId={} paymentId={} amount={}",
                userId, request.paymentId(), request.amount());

        String lockKey = "payment:confirm:lock:" + request.paymentId();
        if (!redisUtil.acquireLock(lockKey)) {
            log.warn("[결제] Lock 획득 실패 (이미 처리 중) userId={} paymentKey={}", userId, request.paymentId());
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING);
        }

        Payment pendingPayment = null;
        try {
            // 1. 중복 결제 체크 + PENDING 조회 (짧은 트랜잭션)
            pendingPayment = paymentTransactionService.savePendingPayment(userId, request);

            // 2. 포트원 V2 SDK 조회 (트랜잭션 밖 — DB 커넥션 미점유)
            log.info("[결제] 포트원 SDK 검증 시작 paymentKey={}", request.paymentId());
            io.portone.sdk.server.payment.Payment portOnePayment =
                    paymentClient.getPayment(request.paymentId()).get(10, TimeUnit.SECONDS);

            // 3. 결제 완료(PAID) 상태 확인
            if (!(portOnePayment instanceof PaidPayment paidPayment)) {
                paymentTransactionService.failPayment(pendingPayment.getId());
                notificationProducer.publish(NotificationEvent.paymentFailed(userId, "포인트 충전", pendingPayment.getId()));
                log.warn("[결제] 결제 미완료 상태 userId={} portoneType={}",
                        userId, portOnePayment.getClass().getSimpleName());
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_INVALID_PENDING);
            }

            // 4. 금액 위변조 검증
            long actualAmount = paidPayment.getAmount().getTotal();
            if (actualAmount != request.amount()) {
                paymentTransactionService.failPayment(pendingPayment.getId());
                notificationProducer.publish(NotificationEvent.paymentFailed(userId, "포인트 충전", pendingPayment.getId()));
                log.warn("[결제] 금액 위변조 감지 userId={} 요청금액={} 실제금액={}",
                        userId, request.amount(), actualAmount);
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_AMOUNT_MISMATCH);
            }
            log.info("[결제] 금액 검증 통과 amount={}", request.amount());

            // 5. COMPLETED 전환 + 포인트 충전 + 구매 이력 저장 (짧은 트랜잭션)
            PaymentMethod resolvedMethod = PaymentMethod.from(paidPayment.getMethod());
            Payment completedPayment = paymentTransactionService.completePayment(
                    pendingPayment.getId(), userId, request.amount(), resolvedMethod
            );
            log.info("[결제] 결제 프로세스 완료 userId={} dbPaymentId={} amount={}",
                    userId, completedPayment.getId(), request.amount());

            notificationProducer.publish(NotificationEvent.pointCharge(
                    userId, request.amount(), pointService.getBalance(userId)
            ));

            return PaymentResponse.from(completedPayment);

        } catch (ServiceErrorException e) {
            throw e;
        } catch (Exception e) {
            if (pendingPayment != null) {
                paymentTransactionService.failPayment(pendingPayment.getId());
            }
            log.error("[결제] 예상치 못한 오류 userId={} paymentKey={}", userId, request.paymentId(), e);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PORTONE_API_ERROR);
        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }

    /**
     * 전액 환불
     *
     * 흐름:
     * 1. [Redis Lock] paymentId 단위로 락 획득 — 중복 환불 요청의 포인트 2중 차감 방지
     * 2. [TX-readOnly] 결제 조회 + 환불 가능 상태 검증
     * 3. [TX] 포인트 선차감 — 잔액 부족 시 여기서 실패하여 PortOne 미호출
     * 4. [외부 API] 포트원 V2 SDK로 환불 요청
     *    - 실패 시 → 선차감한 포인트 복구 후 에러 반환 (보상 트랜잭션)
     * 5. [TX] REFUNDED 전환 (포인트는 이미 차감됨)
     */
    public PaymentResponse cancelPayment(Long userId, Long paymentId, String reason) {
        log.info("[환불] 요청 시작 userId={} paymentId={} reason={}", userId, paymentId, reason);

        String lockKey = "payment:cancel:lock:" + paymentId;
        if (!redisUtil.acquireLock(lockKey)) {
            log.warn("[환불] Lock 획득 실패 (이미 처리 중) userId={} paymentId={}", userId, paymentId);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_CANCELING);
        }

        try {
            // 1. 결제 조회 + 상태 검증 (읽기 전용 트랜잭션)
            Payment payment = paymentTransactionService.getPaymentForCancel(userId, paymentId);
            log.info("[환불] 결제 조회 완료 paymentKey={} status={} amount={}",
                    payment.getPaymentKey(), payment.getStatus(), payment.getAmount());

            // 2. 포인트 선차감 — 잔액 부족 시 여기서 실패, PortOne 미호출
            pointService.deduct(userId, payment.getAmount());
            log.info("[환불] 포인트 선차감 완료 userId={} amount={}P", userId, payment.getAmount());

            try {
                // 3. 포트원 SDK 환불 요청 (트랜잭션 밖 — DB 커넥션 미점유)
                paymentClient.cancelPayment(payment.getPaymentKey(), null, null, null,
                        reason, null, null, null, null, null, null).get(20, TimeUnit.SECONDS);
                log.info("[환불] 포트원 환불 완료 paymentKey={}", payment.getPaymentKey());
            } catch (Exception e) {
                // 보상: PortOne 실패 시 선차감한 포인트 복구
                pointService.compensateDeduct(userId, payment.getAmount());
                log.error("[환불] 포트원 환불 실패 → 포인트 복구 완료 userId={} amount={}P paymentKey={}",
                        userId, payment.getAmount(), payment.getPaymentKey(), e);
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_PORTONE_API_ERROR);
            }

            // 4. REFUNDED 전환 (포인트는 이미 차감됨, 짧은 트랜잭션)
            Payment cancelledPayment = paymentTransactionService.finalizeCancel(payment.getId());
            notificationProducer.publish(NotificationEvent.paymentRefunded(userId, payment.getAmount(), paymentId));
            log.info("[환불] 환불 프로세스 완료 userId={} dbPaymentId={}", userId, paymentId);
            return PaymentResponse.from(cancelledPayment);

        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }


}
