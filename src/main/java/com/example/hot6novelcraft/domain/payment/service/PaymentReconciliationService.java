package com.example.hot6novelcraft.domain.payment.service;

import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentMethod;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.payment.repository.PaymentRepository;
import io.portone.sdk.server.payment.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 웹훅 유실 등으로 정합성이 깨진 결제를 주기적으로 재검증하는 배치 서비스.
 *
 * 재검증 대상:
 *   1. PENDING 30분 초과 — /confirm 미호출 + 웹훅 유실 케이스
 *   2. FAILED 30분~2시간 이내 — confirm 타임아웃으로 잘못 FAILED 처리된 케이스
 *
 * 포트원 API 조회 결과에 따라:
 *   - PaidPayment   → COMPLETED + 포인트 충전
 *   - Failed/Cancelled → FAILED 유지 or PENDING→FAILED
 *   - 그 외 (PENDING 등) → 스킵
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentClient paymentClient;
    private final RedisUtil redisUtil;

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyMinAgo = now.minusMinutes(30);
        LocalDateTime twoHoursAgo = now.minusHours(2);

        List<Payment> stalePending = paymentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, thirtyMinAgo);

        // confirm 타임아웃으로 잘못 FAILED된 최근 건 (30분~2시간 이내)
        List<Payment> recentFailed = paymentRepository
                .findByStatusAndCreatedAtBetween(PaymentStatus.FAILED, twoHoursAgo, thirtyMinAgo);

        int total = stalePending.size() + recentFailed.size();
        if (total == 0) return;

        log.info("[재검증] 배치 시작 — PENDING {} 건, FAILED {} 건", stalePending.size(), recentFailed.size());

        stalePending.forEach(p -> reconcileOne(p, "PENDING"));
        recentFailed.forEach(p -> reconcileOne(p, "FAILED"));

        log.info("[재검증] 배치 완료");
    }

    private void reconcileOne(Payment payment, String reason) {
        // /confirm, 웹훅과 동일한 락 키로 상호 배제 — 동시 처리 시 이중 포인트 충전 방지
        String lockKey = "payment:confirm:lock:" + payment.getPaymentKey();
        if (!redisUtil.acquireLock(lockKey)) {
            log.info("[재검증] 스킵 — 처리 중인 결제 paymentKey={}", payment.getPaymentKey());
            return;
        }

        try {
            io.portone.sdk.server.payment.Payment portOnePayment =
                    paymentClient.getPayment(payment.getPaymentKey()).get(10, TimeUnit.SECONDS);

            if (portOnePayment instanceof PaidPayment paidPayment) {
                PaymentMethod method = PaymentMethod.from(paidPayment.getMethod());
                paymentTransactionService.completePayment(
                        payment.getId(), payment.getUserId(), payment.getAmount(), method
                );
                log.info("[재검증] 결제 완료 보정 reason={} paymentKey={} userId={}",
                        reason, payment.getPaymentKey(), payment.getUserId());

            } else if (portOnePayment instanceof FailedPayment
                    || portOnePayment instanceof CancelledPayment) {
                paymentTransactionService.failPayment(payment.getId());
                log.info("[재검증] 결제 실패 보정 reason={} paymentKey={}", reason, payment.getPaymentKey());

            } else if (portOnePayment instanceof PartialCancelledPayment) {
                // 부분 취소는 서비스 정책상 미지원 — 상태 변경 없이 스킵
                log.warn("[재검증] 부분 취소 상태 (미지원) 스킵 paymentKey={}", payment.getPaymentKey());

            } else {
                log.info("[재검증] 스킵 — 포트원 상태 미확정 paymentKey={} portOneType={}",
                        payment.getPaymentKey(), portOnePayment.getClass().getSimpleName());
            }

        } catch (Exception e) {
            log.error("[재검증] 포트원 조회 실패 paymentKey={}", payment.getPaymentKey(), e);
        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }
}
