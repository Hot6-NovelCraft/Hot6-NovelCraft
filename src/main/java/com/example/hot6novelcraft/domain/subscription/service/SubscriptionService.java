package com.example.hot6novelcraft.domain.subscription.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.SubscriptionExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import com.example.hot6novelcraft.domain.notification.producer.NotificationProducer;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionCancelRequest;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionCompleteRequest;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionPrepareRequest;
import com.example.hot6novelcraft.domain.subscription.dto.response.SubscriptionPrepareResponse;
import com.example.hot6novelcraft.domain.subscription.dto.response.SubscriptionResponse;
import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.portone.sdk.server.payment.PaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionTransactionService subscriptionTransactionService;
    private final PaymentClient paymentClient;
    private final RedisUtil redisUtil;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationProducer notificationProducer;

    @Value("${portone.api-secret}")
    private String portoneApiSecret;

    /**
     * 구독 준비 - subscriptionKey 발급
     */
    public SubscriptionPrepareResponse prepareSubscription(Long userId, SubscriptionPrepareRequest request) {
        // 1. 중복 구독 체크
        subscriptionTransactionService.validateNotSubscribed(userId);

        // 2. 금액 검증 (클라이언트 금액 vs 플랜 정가)
        request.planType().validateAmount(request.amount());
        log.info("[구독 준비] 금액 검증 통과 planType={} amount={}", request.planType(), request.amount());

        // 3. subscriptionKey 생성 (UUID 기반, 36자)
        String subscriptionKey = UUID.randomUUID().toString();

        // 4. PENDING Subscription 생성
        Subscription subscription = subscriptionTransactionService.prepareSubscription(
                userId,
                request.planType(),
                subscriptionKey,
                request.amount()
        );

        log.info("[구독 준비] userId={}, subscriptionKey={}", userId, subscriptionKey);

        return new SubscriptionPrepareResponse(
                subscription.getSubscriptionKey(),
                subscription.getAmount(),
                subscription.getPlanType()
        );
    }

    /**
     * 구독 완료 - 빌링키로 첫 결제 + 구독 활성화
     */
    public SubscriptionResponse completeSubscription(Long userId, SubscriptionCompleteRequest request) {
        // 1. PENDING Subscription 조회 + 검증 (Lock 전에 먼저 조회하여 subscriptionId 확보)
        Subscription subscription = subscriptionTransactionService.getSubscriptionForComplete(
                userId,
                request.subscriptionKey()
        );

        // ⚠️ subscriptionId 기반 Lock (WebhookService와 동일한 키 패턴)
        String lockKey = "subscription:complete:lock:" + subscription.getId();

        // Redis Lock 획득
        if (!redisUtil.acquireLock(lockKey)) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_PROCESSING);
        }

        try {
            // 🔒 Lock 획득 후 상태 재검증 (방어적 프로그래밍)
            // Lock 획득 전~후 사이에 다른 요청이 이미 처리했을 가능성 방어
            subscriptionTransactionService.validateSubscriptionStillPending(subscription.getId());

            // 2. 빌링키로 첫 결제 실행
            // paymentKey에 subscriptionId 포함 (웹훅에서 구독 결제 구분용)
            String paymentKey = "subscription-" + subscription.getId() + "-" + UUID.randomUUID();
            Long paymentDbId = executeBillingPayment(
                    userId,
                    paymentKey,
                    subscription.getAmount(),
                    request.billingKey(),
                    subscription.getId()
            );

            // 3. Subscription ACTIVE 전환
            Subscription completedSubscription = subscriptionTransactionService.completeSubscription(
                    request.subscriptionKey(),
                    request.billingKey(),
                    paymentDbId
            );

            log.info("[구독 완료] userId={}, subscriptionId={}, billingKey={}",
                    userId, completedSubscription.getId(), request.billingKey());

            notificationProducer.publish(
                    NotificationEvent.subscriptionActivated(userId, completedSubscription.getId())
            );

            return SubscriptionResponse.from(completedSubscription);

        } catch (ServiceErrorException e) {
            throw e;
        } catch (Exception e) {
            log.error("[구독 완료 실패] userId={}, subscriptionKey={}", userId, request.subscriptionKey(), e);
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_FIRST_PAYMENT_FAILED);
        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }

    /**
     * 빌링키로 결제 실행 (PortOne REST API 직접 호출)
     * POST https://api.portone.io/payments/{paymentId}/billing-key
     *
     * @return Payment DB ID
     */
    private Long executeBillingPayment(Long userId, String paymentKey, Long amount, String billingKey, Long subscriptionId) {
        String url = "https://api.portone.io/payments/" + paymentKey + "/billing-key";

        try {
            log.info("[빌링키 결제] 시작 paymentKey={} billingKey={} amount={}",
                    paymentKey, billingKey, amount);

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "PortOne " + portoneApiSecret);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 요청 바디 생성
            String requestBody = String.format("""
                    {
                        "billingKey": "%s",
                        "orderName": "정기 구독 결제",
                        "amount": {
                            "total": %d
                        },
                        "currency": "KRW"
                    }
                    """, billingKey, amount);

            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            // POST 요청
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.debug("[빌링키 결제] PortOne 응답 바디: {}", response.getBody());

                // TossPayments는 빌링키 결제 초기 응답에 status 없이 pgTxId/paidAt만 반환하므로
                // GET /payments/{paymentId} 로 최종 상태를 별도 조회
                String getUrl = "https://api.portone.io/payments/" + paymentKey;
                ResponseEntity<String> getResponse = restTemplate.exchange(
                        getUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class
                );
                log.debug("[빌링키 결제] 결제 조회 응답: {}", getResponse.getBody());

                JsonNode fetchedPayment = objectMapper.readTree(getResponse.getBody());
                String paymentStatus = fetchedPayment.path("status").asText();

                if (!"PAID".equals(paymentStatus)) {
                    String failureReason = fetchedPayment.path("failure").path("reason").asText("알 수 없음");
                    String pgMessage = fetchedPayment.path("failure").path("pgMessage").asText("");
                    log.error("[빌링키 결제 실패] PortOne 상태={}, 사유={}, PG메시지={}, paymentKey={}",
                            paymentStatus, failureReason, pgMessage, paymentKey);
                    throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
                }

                log.info("[빌링키 결제 성공] paymentKey={}, status={}", paymentKey, paymentStatus);

                // Payment와 Purchase를 단일 트랜잭션으로 생성 (원자성 보장)
                var payment = subscriptionTransactionService.createPaymentAndPurchase(userId, paymentKey, amount);

                // ⚠️ 주의: 구독료는 포인트 충전이 아니라 프리미엄 혜택 구매
                // 포인트 충전은 일반 결제에서만 수행

                return payment.getId();
            } else {
                log.error("[빌링키 결제 실패] 예상치 못한 HTTP 상태 paymentKey={}, status={}",
                        paymentKey, response.getStatusCode());
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            }

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                // 400 - 잘못된 요청
                log.error("[빌링키 결제 실패] 잘못된 요청 paymentKey={}, error={}",
                        paymentKey, e.getResponseBodyAsString());
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                // 401 - 인증 오류
                log.error("[빌링키 결제 실패] 인증 오류 paymentKey={}", paymentKey);
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                // 403 - 권한 오류
                log.error("[빌링키 결제 실패] 권한 오류 paymentKey={}", paymentKey);
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // 404 - 빌링키 없음
                log.error("[빌링키 결제 실패] 빌링키 없음 billingKey={}", billingKey);
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_BILLING_KEY_ISSUE_FAILED);
            } else if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // 409 - 이미 결제됨, 빌링키 삭제됨 등
                log.error("[빌링키 결제 실패] 충돌 오류 paymentKey={}, error={}",
                        paymentKey, e.getResponseBodyAsString());
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            } else {
                log.error("[빌링키 결제 실패] HTTP 오류 paymentKey={}, status={}",
                        paymentKey, e.getStatusCode());
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            }
        } catch (HttpServerErrorException e) {
            // 502 - PG Provider 오류
            log.error("[빌링키 결제 실패] 서버 오류 paymentKey={}, status={}",
                    paymentKey, e.getStatusCode());
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
        } catch (ServiceErrorException e) {
            throw e;
        } catch (Exception e) {
            log.error("[빌링키 결제 실패] 예상치 못한 오류 billingKey={}", billingKey, e);
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
        }
    }

    /**
     * 구독 취소 - 빌링키 삭제 + 구독 취소
     */
    public void cancelSubscription(Long userId, Long subscriptionId, SubscriptionCancelRequest request) {
        String lockKey = "subscription:cancel:lock:" + subscriptionId;

        // Redis Lock 획득
        if (!redisUtil.acquireLock(lockKey)) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_PROCESSING);
        }

        try {
            // 1. 구독 조회 + 검증
            Subscription subscription = subscriptionTransactionService.getSubscriptionForCancel(userId, subscriptionId);

            // 2. PortOne 빌링키 삭제 시도 (실패해도 계속 진행)
            try {
                deleteBillingKey(subscription.getBillingKey());
                log.info("[빌링키 삭제 성공] subscriptionId={}, billingKey={}",
                        subscriptionId, subscription.getBillingKey());
            } catch (Exception e) {
                log.error("[빌링키 삭제 실패] subscriptionId={}, billingKey={} - 구독 취소는 계속 진행",
                        subscriptionId, subscription.getBillingKey(), e);
            }

            // 3. Subscription CANCELLED 전환
            subscriptionTransactionService.cancelSubscription(subscriptionId);

            log.info("[구독 취소] userId={}, subscriptionId={}", userId, subscriptionId);

            notificationProducer.publish(
                    NotificationEvent.subscriptionCancelled(userId, subscriptionId)
            );

        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }

    /**
     * 빌링키 삭제 (PortOne REST API 직접 호출)
     * DELETE https://api.portone.io/billing-keys/{billingKey}
     * <p>
     * PortOne SDK 0.23.0에 deleteBillingKey 메서드가 없어 REST API를 직접 호출합니다.
     */
    private void deleteBillingKey(String billingKey) {
        // URL 쿼리 파라미터 안전하게 인코딩 (한글 "구독 취소" → URL 인코딩)
        String url = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl("https://api.portone.io/billing-keys/" + billingKey)
                .queryParam("reason", "구독 취소")
                .encode()
                .toUriString();

        try {
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "PortOne " + portoneApiSecret);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // DELETE 요청
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("[빌링키 삭제 성공] billingKey={}", billingKey);
            }

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // 404 - 빌링키가 존재하지 않음 (정상 처리)
                log.warn("[빌링키 삭제] 빌링키 없음 billingKey={}", billingKey);
            } else if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // 409 - 이미 삭제된 빌링키 (정상 처리)
                log.warn("[빌링키 삭제] 이미 삭제된 빌링키 billingKey={}", billingKey);
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                // 400 - 잘못된 요청
                log.error("[빌링키 삭제 실패] 잘못된 요청 billingKey={}, error={}",
                        billingKey, e.getResponseBodyAsString());
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                // 401 - 인증 오류
                log.error("[빌링키 삭제 실패] 인증 오류 billingKey={}", billingKey);
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                // 403 - 권한 오류
                log.error("[빌링키 삭제 실패] 권한 오류 billingKey={}", billingKey);
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            } else {
                log.error("[빌링키 삭제 실패] HTTP 오류 billingKey={}, status={}",
                        billingKey, e.getStatusCode());
                throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
            }
        } catch (HttpServerErrorException e) {
            // 502 - PG Provider 오류
            log.error("[빌링키 삭제 실패] 서버 오류 billingKey={}, status={}",
                    billingKey, e.getStatusCode());
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
        } catch (Exception e) {
            log.error("[빌링키 삭제 실패] 예상치 못한 오류 billingKey={}", billingKey, e);
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR);
        }
    }

    /**
     * 내 구독 조회
     */
    public SubscriptionResponse getMySubscription(Long userId) {
        Subscription subscription = subscriptionTransactionService.getActiveSubscription(userId)
                .orElseThrow(() -> new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND));

        return SubscriptionResponse.from(subscription);
    }

    /**
     * 정기 청구 처리 (스케줄러용)
     * ⚠️ @Transactional 없음 - 외부 API 호출 포함
     */
    public void processBillingForSubscription(Subscription subscription) {
        String lockKey = "subscription:billing:lock:" + subscription.getId();

        // Redis Lock 획득
        if (!redisUtil.acquireLock(lockKey)) {
            log.warn("[정기 청구] 락 획득 실패 subscriptionId={}", subscription.getId());
            return;
        }

        try {
            // 1. 빌링키로 결제 실행
            // paymentKey에 subscriptionId 포함 (웹훅에서 구독 결제 구분용)
            String paymentKey = "subscription-" + subscription.getId() + "-" + UUID.randomUUID();
            Long paymentDbId = executeBillingPayment(
                    subscription.getUserId(),
                    paymentKey,
                    subscription.getAmount(),
                    subscription.getBillingKey(),
                    subscription.getId()
            );

            // 2. Subscription 업데이트 (nextBillingAt += 1개월)
            subscriptionTransactionService.updateSubscriptionAfterPayment(
                    subscription.getBillingKey(),
                    paymentDbId
            );

            log.info("[정기 청구 성공] subscriptionId={}, paymentId={}",
                    subscription.getId(), paymentDbId);

        } catch (Exception e) {
            // 1회 실패 시 즉시 취소
            log.error("[정기 청구 실패] subscriptionId={} - 구독 취소", subscription.getId(), e);
            subscriptionTransactionService.cancelSubscriptionDueToPaymentFailure(
                    subscription.getId(),
                    "정기 결제 실패: " + e.getMessage()
            );
        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }
}
