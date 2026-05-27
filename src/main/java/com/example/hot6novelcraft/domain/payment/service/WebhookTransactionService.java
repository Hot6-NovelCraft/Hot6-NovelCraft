package com.example.hot6novelcraft.domain.payment.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.SubscriptionExceptionEnum;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentMethod;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.payment.repository.PaymentRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.example.hot6novelcraft.domain.purchases.entity.Purchase;
import com.example.hot6novelcraft.domain.purchases.entity.enums.PurchaseType;
import com.example.hot6novelcraft.domain.purchases.repository.PurchaseRepository;
import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.example.hot6novelcraft.domain.subscription.repository.SubscriptionRepository;
import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEvent;
import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEventStatus;
import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEventType;
import com.example.hot6novelcraft.domain.webhookevent.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹훅 관련 DB 트랜잭션 전담 서비스.
 * 외부 API 호출 없이 DB 작업만 수행하여 트랜잭션 점유 시간을 최소화한다.
 * 오케스트레이션은 {@link WebhookService}가 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookTransactionService {

    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PointService pointService;

    /**
     * 멱등성 체크 후 WebhookEvent를 준비한다.
     *
     * @return null이면 이미 처리 완료된 이벤트 (처리 skip),
     * non-null이면 신규 또는 재시도 이벤트 (처리 계속)
     */
    @Transactional
    public WebhookEvent prepareWebhookEvent(String transactionId, WebhookEventType eventType,
                                            String paymentId, String rawPayload) {
        WebhookEvent existing = webhookEventRepository.findByWebhookId(transactionId).orElse(null);

        if (existing != null && existing.getStatus() == WebhookEventStatus.COMPLETE) {
            log.info("웹훅 중복 수신 무시 transactionId={}", transactionId);
            return null;
        }

        if (existing != null) {
            return existing;
        }

        WebhookEvent event = WebhookEvent.create(transactionId, eventType, paymentId, rawPayload);
        webhookEventRepository.save(event);
        log.info("웹훅 이벤트 생성 transactionId={} type={}", transactionId, eventType);
        return event;
    }

    /**
     * paymentKey로 Payment를 조회한다.
     */
    @Transactional(readOnly = true)
    public Payment getPaymentByKey(String paymentKey) {
        return paymentRepository.findByPaymentKey(paymentKey).orElse(null);
    }

    /**
     * WebhookEvent를 COMPLETE 상태로 전환한다.
     */
    @Transactional
    public void markEventComplete(Long webhookEventId) {
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.complete();
            log.info("웹훅 이벤트 COMPLETE 처리 webhookEventId={}", webhookEventId);
        });
    }

    /**
     * WebhookEvent를 FAIL 상태로 전환한다.
     */
    @Transactional
    public void markEventFailed(Long webhookEventId, String reason) {
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.fail(reason);
            log.info("웹훅 이벤트 FAIL 처리 webhookEventId={} reason={}", webhookEventId, reason);
        });
    }

    /**
     * [결제 실패 처리] PENDING 상태인 결제를 FAILED로 전환한다.
     * 포인트 변동 없이 상태만 갱신한다.
     */
    @Transactional
    public void failPendingPayment(Long webhookEventId, Long paymentDbId) {
        paymentRepository.findById(paymentDbId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.fail();
                log.info("결제 실패 처리 paymentDbId={}", paymentDbId);
            }
        });
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.complete();
            log.info("웹훅 이벤트 COMPLETE 처리 webhookEventId={}", webhookEventId);
        });
    }

    /**
     * [/confirm 누락 보정] PENDING 상태인 결제를 COMPLETED로 전환하고 포인트를 충전한다.
     * Redis Lock으로 /confirm과의 상호 배제가 보장되므로 원자적 UPDATE 없이 직접 전환한다.
     */
    @Transactional
    public void completePendingPayment(Long webhookEventId, Long paymentDbId, PaymentMethod method) {
        Payment payment = paymentRepository.findById(paymentDbId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND));

        // 이미 COMPLETED면 멱등성 보장
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("웹훅 보정 스킵 - 이미 COMPLETED paymentDbId={}", paymentDbId);
            webhookEventRepository.findById(webhookEventId).ifPresent(WebhookEvent::complete);
            return;
        }

        // PENDING 또는 FAILED(confirm 타임아웃으로 잘못 처리된 케이스) 허용, 그 외 skip
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED) {
            log.warn("웹훅 보정 스킵 - 처리 불가 상태 paymentDbId={} status={}", paymentDbId, payment.getStatus());
            webhookEventRepository.findById(webhookEventId).ifPresent(WebhookEvent::complete);
            return;
        }

        payment.complete(method);

        pointService.charge(payment.getUserId(), payment.getAmount());
        purchaseRepository.save(
                Purchase.create(payment.getUserId(), PurchaseType.POINT, payment.getAmount(), paymentDbId)
        );
        webhookEventRepository.findById(webhookEventId).ifPresent(WebhookEvent::complete);
        log.info("웹훅 보정 완료 (/confirm 누락) paymentDbId={} userId={}", paymentDbId, payment.getUserId());
    }

    /**
     * [구독 결제 웹훅 보정] PENDING 상태인 구독 결제를 COMPLETED로 전환하고 포인트 충전 + Subscription 업데이트
     * Redis Lock으로 중복 처리가 방지되므로 원자적 UPDATE 없이 직접 전환한다.
     */
    @Transactional
    public void completePendingSubscriptionPayment(Long webhookEventId, Long paymentDbId, PaymentMethod method, Long subscriptionId) {
        Payment payment = paymentRepository.findById(paymentDbId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND));

        // 이미 COMPLETED면 멱등성 보장
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("웹훅 구독 결제 보정 스킵 - 이미 COMPLETED paymentDbId={}", paymentDbId);
            webhookEventRepository.findById(webhookEventId).ifPresent(WebhookEvent::complete);
            return;
        }

        // PENDING 또는 FAILED(confirm 타임아웃으로 잘못 처리된 케이스) 허용, 그 외 skip
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED) {
            log.warn("웹훅 구독 결제 보정 스킵 - 처리 불가 상태 paymentDbId={} status={}", paymentDbId, payment.getStatus());
            webhookEventRepository.findById(webhookEventId).ifPresent(WebhookEvent::complete);
            return;
        }

        // 1. Payment COMPLETED 전환
        payment.complete(method);

        // 2. Purchase 저장 (구독 타입)
        // ⚠️ 구독료는 포인트 충전이 아니라 프리미엄 혜택 구매
        purchaseRepository.save(
                Purchase.create(payment.getUserId(), PurchaseType.SUBSCRIPTION, payment.getAmount(), paymentDbId)
        );

        // 3. Subscription nextBillingAt 업데이트
        if (subscriptionId != null) {
            Subscription subscription = subscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND));
            subscription.updateAfterPayment(paymentDbId);
            subscriptionRepository.save(subscription);
            log.info("구독 정기 청구 완료 subscriptionId={} nextBillingAt={}", subscriptionId, subscription.getNextBillingAt());
        }

        // 4. WebhookEvent COMPLETE
        webhookEventRepository.findById(webhookEventId).ifPresent(WebhookEvent::complete);
        log.info("웹훅 구독 결제 보정 완료 paymentDbId={} subscriptionId={} userId={}",
                paymentDbId, subscriptionId, payment.getUserId());
    }

    /**
     * [환불 타임아웃 보정] COMPLETED 결제를 REFUNDED로 전환한다.
     * <p>
     * cancelPayment()에서 포트원 취소 API 타임아웃 발생 시:
     * - compensateDeduct()로 포인트 복구 후 Payment가 COMPLETED로 남는 케이스
     * - 이후 Transaction.Cancelled 웹훅 도착 시 이 메서드로 보정
     * <p>
     * 포인트 처리 전략:
     * - compensateDeduct 실행됨 → 포인트 잔액 있음 → deduct 성공 → 정상 차감
     * - compensateDeduct 미실행 (서버 다운 등) → 이미 차감된 상태 → deduct 잔액 부족 예외 → 스킵
     * 두 경우 모두 최종적으로 REFUNDED 상태가 되어 포트원 상태와 일치
     */
    @Transactional
    public void finalizeRefundFromWebhook(Long webhookEventId, Long paymentDbId) {
        Payment payment = paymentRepository.findById(paymentDbId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            log.info("웹훅 환불 보정 스킵 - 이미 처리됨 paymentDbId={} status={}", paymentDbId, payment.getStatus());
            webhookEventRepository.findById(webhookEventId).ifPresent(WebhookEvent::complete);
            return;
        }

        try {
            pointService.deduct(payment.getUserId(), payment.getAmount());
            log.info("웹훅 환불 보정: 포인트 차감 완료 userId={} amount={}P", payment.getUserId(), payment.getAmount());
        } catch (ServiceErrorException e) {
            // compensateDeduct 실행 여부를 알 수 없어 "이미 차감됨"으로 단정 불가.
            // ERR_INSUFFICIENT_POINT 이어도 compensateDeduct 실행 후 사용자가 포인트를 소비한 케이스이면
            // payment.cancel()까지 가면 환불 금액 + 포인트 소비를 둘 다 취득하는 손실이 발생한다.
            // ERR_POINT_NOT_FOUND 등 다른 오류도 마찬가지로 데이터 정합성 문제.
            // 모든 차감 오류는 FAIL로 남겨 수동 보정 경로로 보낸다.
            log.error("웹훅 환불 보정 실패: 포인트 차감 오류 — 수동 보정 필요 userId={} amount={}P error={}",
                    payment.getUserId(), payment.getAmount(), e.getMessage());
            webhookEventRepository.findById(webhookEventId).ifPresent(event -> event.fail(e.getMessage()));
            return;
        }

        payment.cancel();
        webhookEventRepository.findById(webhookEventId).ifPresent(WebhookEvent::complete);
        log.info("웹훅 환불 보정 완료 (COMPLETED→REFUNDED) paymentDbId={} userId={}", paymentDbId, payment.getUserId());
    }
}
