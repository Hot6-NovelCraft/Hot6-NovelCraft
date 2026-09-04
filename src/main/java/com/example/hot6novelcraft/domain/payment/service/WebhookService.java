package com.example.hot6novelcraft.domain.payment.service;

import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.payment.dto.request.WebhookRequest;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentMethod;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.subscription.service.SubscriptionTransactionService;
import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEvent;
import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.portone.sdk.server.payment.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 웹훅 처리 오케스트레이터.
 * DB 트랜잭션({@link WebhookTransactionService})과 외부 API 호출을 분리하여
 * 트랜잭션 점유 중 외부 API 대기가 발생하지 않도록 한다.
 * <p>
 * 멱등성 키 전략:
 * - Transaction.Paid / Transaction.Failed → transactionId (결제 트랜잭션 ID)
 * - Transaction.Cancelled → cancellationId (취소 트랜잭션 ID)
 * 포트원 V2는 취소 웹훅에 결제와 동일한 transactionId를 전송하므로
 * cancellationId를 별도 키로 사용해야 중복 처리를 막을 수 있다.
 * <p>
 * 처리 흐름:
 * 1. [TX] 멱등성 체크 + WebhookEvent 준비
 * 2. [외부 API] 포트원 SDK 결제 상태 조회 (위조 방지 검증)
 * 3. [TX-readOnly] Payment 조회
 * 4. 상태에 따라 분기
 * - Payment 없음:    /prepare 전에 웹훅이 도착한 예외 케이스 → WebhookEvent COMPLETE (로그만 남김)
 * - Payment PENDING: /confirm 누락 보정 → completePendingPayment / failPendingPayment
 * - Payment 최종상태: /confirm 또는 /cancel이 이미 처리 완료 → WebhookEvent COMPLETE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookTransactionService webhookTransactionService;
    private final SubscriptionTransactionService subscriptionTransactionService;
    private final PaymentClient paymentClient;
    private final ObjectMapper objectMapper;
    private final RedisUtil redisUtil;

    public void handleWebhook(WebhookRequest request) {
        if (!isProcessableType(request.type())) {
            log.debug("웹훅 이벤트 무시 type={}", request.type());
            return;
        }

        if (request.data() == null || request.data().paymentId() == null) {
            log.warn("웹훅 페이로드 비정상 type={}", request.type());
            return;
        }

        String paymentId = request.data().paymentId();
        String transactionId = request.data().transactionId();
        String idempotencyKey = "Transaction.Cancelled".equals(request.type())
                ? request.data().cancellationId()
                : transactionId;
        log.info("포트원 V2 웹훅 수신 paymentId={} transactionId={} type={}", paymentId, transactionId, request.type());

        if (idempotencyKey == null) {
            log.warn("웹훅 멱등성 키 누락 type={} paymentId={}", request.type(), paymentId);
            return;
        }

        // 1. 멱등성 체크 + WebhookEvent 준비 (짧은 트랜잭션)
        WebhookEventType eventType = parseEventType(request.type());
        String rawPayload = serializePayload(request);
        WebhookEvent webhookEvent = webhookTransactionService.prepareWebhookEvent(
                idempotencyKey, eventType, paymentId, rawPayload
        );
        if (webhookEvent == null) {
            return;
        }

        // 2. 포트원 SDK 조회로 실제 결제 상태 검증 (위조 방지)
        io.portone.sdk.server.payment.Payment portOnePayment;
        try {
            portOnePayment = paymentClient.getPayment(paymentId).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("웹훅 처리 중 포트원 SDK 조회 실패 paymentId={}", paymentId, e);
            webhookTransactionService.markEventFailed(webhookEvent.getId(), "포트원 SDK 조회 실패: " + e.getMessage());
            return;
        }

        // 3. Payment 조회 (읽기 전용 짧은 트랜잭션)
        Payment payment = webhookTransactionService.getPaymentByKey(paymentId);

        if (payment == null) {
            // /confirm이 아직 도착하지 않은 상태 — userId를 알 수 없으므로 보정 불가
            // 정상 케이스라면 이후 /confirm 호출 시 처리됨
            log.warn("웹훅 수신 시점에 Payment 미존재 paymentId={} → /confirm 대기", paymentId);
            webhookTransactionService.markEventComplete(webhookEvent.getId());
            return;
        }

        // 4. 포트원 상태 기준으로 분기 처리
        if (portOnePayment instanceof PaidPayment paidPayment) {
            // COMPLETED 또는 REFUNDED → 이미 처리됨
            if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.REFUNDED) {
                log.info("웹훅 보정 불필요 - 이미 최종 상태 paymentId={} status={}", paymentId, payment.getStatus());
                webhookTransactionService.markEventComplete(webhookEvent.getId());
                return;
            }
            // PENDING 또는 FAILED(confirm 타임아웃으로 잘못 처리된 케이스) → 결제 완료 보정
            completePaymentFromWebhook(webhookEvent, payment, paidPayment, paymentId);

        } else if (portOnePayment instanceof FailedPayment) {
            // PENDING만 FAILED로 전환, 그 외 이미 최종 상태
            if (payment.getStatus() != PaymentStatus.PENDING) {
                log.info("웹훅 보정 불필요 - FAILED 처리 불가 상태 paymentId={} status={}", paymentId, payment.getStatus());
                webhookTransactionService.markEventComplete(webhookEvent.getId());
                return;
            }
            webhookTransactionService.failPendingPayment(webhookEvent.getId(), payment.getId());
            log.info("웹훅 결제 실패 처리 완료 paymentId={}", paymentId);

        } else if (portOnePayment instanceof CancelledPayment) {
            // REFUNDED / FAILED → 이미 최종 상태
            if (payment.getStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.FAILED) {
                log.info("웹훅 보정 불필요 - 이미 최종 상태 paymentId={} status={}", paymentId, payment.getStatus());
                webhookTransactionService.markEventComplete(webhookEvent.getId());
                return;
            }
            // COMPLETED → 환불 타임아웃 케이스: compensateDeduct 이후 포트원은 취소됐으나 DB가 COMPLETED로 남은 경우
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                log.info("웹훅 환불 보정 시작 (COMPLETED→REFUNDED) paymentId={}", paymentId);
                String cancelLockKey = "payment:cancel:lock:" + paymentId;
                if (!redisUtil.acquireLock(cancelLockKey)) {
                    log.warn("웹훅 환불 보정: Lock 획득 실패 (처리 중) paymentId={} → 포트원이 재시도 예정", paymentId);
                    return;
                }
                try {
                    webhookTransactionService.finalizeRefundFromWebhook(webhookEvent.getId(), payment.getId());
                } finally {
                    redisUtil.releaseLock(cancelLockKey);
                }
                return;
            }
            // PENDING → 결제창 열린 후 완료 전 취소된 케이스
            webhookTransactionService.failPendingPayment(webhookEvent.getId(), payment.getId());
            log.info("웹훅 결제 취소 처리 완료 (PENDING→FAILED) paymentId={}", paymentId);

        } else if (portOnePayment instanceof PartialCancelledPayment) {
            // 부분 취소는 서비스 정책상 미지원 — 결제 상태 변경 없이 이벤트만 완료 처리
            log.warn("웹훅 부분 취소 수신 (미지원) paymentId={} status={}", paymentId, payment.getStatus());
            webhookTransactionService.markEventComplete(webhookEvent.getId());

        } else {
            log.warn("웹훅 알 수 없는 상태 paymentId={} portOneType={}", paymentId, portOnePayment.getClass().getSimpleName());
            webhookTransactionService.markEventFailed(webhookEvent.getId(),
                    "알 수 없는 포트원 상태: " + portOnePayment.getClass().getSimpleName());
        }
    }

    private void completePaymentFromWebhook(WebhookEvent webhookEvent, Payment payment,
                                            PaidPayment paidPayment, String paymentId) {
        PaymentMethod resolvedMethod = PaymentMethod.from(paidPayment.getMethod());
        boolean isSubscriptionPayment = isSubscriptionPayment(paymentId);

        String lockKey;
        Long subscriptionId = null;

        if (isSubscriptionPayment) {
            subscriptionId = extractSubscriptionId(paymentId);
            if (subscriptionId == null) {
                log.error("웹훅 구독 결제 처리 실패: subscriptionId 추출 불가 paymentId={}", paymentId);
                webhookTransactionService.markEventFailed(webhookEvent.getId(),
                        "paymentKey 형식 오류로 subscriptionId 추출 실패");
                return;
            }
            lockKey = "subscription:complete:lock:" + subscriptionId;
        } else {
            lockKey = "payment:confirm:lock:" + paymentId;
        }

        if (!redisUtil.acquireLock(lockKey)) {
            // /confirm이 처리 중 — WebhookEvent를 PENDING으로 두어 포트원 재시도 시 재처리
            log.warn("웹훅: Lock 획득 실패 (처리 중) paymentId={} → 포트원이 재시도 예정", paymentId);
            return;
        }
        try {
            if (isSubscriptionPayment) {
                webhookTransactionService.completePendingSubscriptionPayment(
                        webhookEvent.getId(), payment.getId(), resolvedMethod, subscriptionId);
                log.info("웹훅 구독 결제 보정 완료 paymentId={} subscriptionId={}", paymentId, subscriptionId);
            } else {
                webhookTransactionService.completePendingPayment(webhookEvent.getId(), payment.getId(), resolvedMethod);
                log.info("웹훅 보정 처리 완료 paymentId={} status={}", paymentId, payment.getStatus());
            }
        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }

    private boolean isProcessableType(String type) {
        return "Transaction.Paid".equals(type)
                || "Transaction.Failed".equals(type)
                || "Transaction.Cancelled".equals(type);
    }

    private WebhookEventType parseEventType(String type) {
        return switch (type) {
            case "Transaction.Paid" -> WebhookEventType.TRANSACTION_PAID;
            case "Transaction.Failed" -> WebhookEventType.TRANSACTION_FAILED;
            case "Transaction.Cancelled" -> WebhookEventType.TRANSACTION_CANCELLED;
            default -> WebhookEventType.TRANSACTION_FAILED;
        };
    }

    private String serializePayload(WebhookRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.warn("웹훅 페이로드 직렬화 실패", e);
            return null;
        }
    }

    /**
     * paymentKey가 구독 결제인지 확인
     * 구독 결제는 "subscription-{subscriptionId}-{UUID}" 형식
     */
    private boolean isSubscriptionPayment(String paymentKey) {
        return paymentKey != null && paymentKey.startsWith("subscription-");
    }

    /**
     * paymentKey에서 subscriptionId 추출
     * 형식: "subscription-{subscriptionId}-{UUID}"
     */
    private Long extractSubscriptionId(String paymentKey) {
        try {
            String[] parts = paymentKey.split("-");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (NumberFormatException e) {
            log.error("paymentKey에서 subscriptionId 추출 실패 paymentKey={}", paymentKey, e);
        }
        return null;
    }

}
