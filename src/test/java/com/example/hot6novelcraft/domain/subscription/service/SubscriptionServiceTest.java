package com.example.hot6novelcraft.domain.subscription.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.SubscriptionExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.notification.entity.enums.NotificationType;
import com.example.hot6novelcraft.domain.notification.producer.NotificationProducer;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionCancelRequest;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionCompleteRequest;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionPrepareRequest;
import com.example.hot6novelcraft.domain.subscription.dto.response.SubscriptionPrepareResponse;
import com.example.hot6novelcraft.domain.subscription.dto.response.SubscriptionResponse;
import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.example.hot6novelcraft.domain.subscription.entity.enums.PlanType;
import com.example.hot6novelcraft.domain.subscription.entity.enums.SubscriptionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.portone.sdk.server.payment.PaymentClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionService 테스트")
class SubscriptionServiceTest {

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionTransactionService subscriptionTransactionService;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationProducer notificationProducer;

    private static final Long USER_ID = 1L;
    private static final Long SUBSCRIPTION_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final Long AMOUNT = 9900L;
    private static final String SUBSCRIPTION_KEY = "test-subscription-key-123";
    private static final String BILLING_KEY = "test-billing-key-123";
    private static final String PORTONE_API_SECRET = "test-api-secret";

    private Subscription createMockSubscription(Long id, Long userId, SubscriptionStatus status, String subscriptionKey, String billingKey) {
        Subscription subscription = mock(Subscription.class);
        given(subscription.getId()).willReturn(id);
        given(subscription.getUserId()).willReturn(userId);
        given(subscription.getSubscriptionStatus()).willReturn(status);
        given(subscription.getPlanType()).willReturn(PlanType.PREMIUM);
        given(subscription.getSubscriptionKey()).willReturn(subscriptionKey);
        given(subscription.getBillingKey()).willReturn(billingKey);
        given(subscription.getAmount()).willReturn(AMOUNT);
        given(subscription.getStartedAt()).willReturn(LocalDateTime.now());
        given(subscription.getNextBillingAt()).willReturn(LocalDateTime.now().plusMonths(1));
        return subscription;
    }

    private Payment createMockPayment(Long id, Long userId, Long amount) {
        Payment payment = mock(Payment.class);
        given(payment.getId()).willReturn(id);
        given(payment.getUserId()).willReturn(userId);
        given(payment.getAmount()).willReturn(amount);
        return payment;
    }

    // =========================================================
    // prepareSubscription() - 구독 준비
    // =========================================================
    @Nested
    @DisplayName("prepareSubscription() - 구독 준비")
    class PrepareSubscriptionTest {

        @Test
        @DisplayName("성공 - 구독 준비 성공")
        void prepareSubscription_success() {
            // given
            SubscriptionPrepareRequest request = new SubscriptionPrepareRequest(PlanType.PREMIUM, AMOUNT);

            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.PENDING, SUBSCRIPTION_KEY, null);

            doNothing().when(subscriptionTransactionService).validateNotSubscribed(USER_ID);
            given(subscriptionTransactionService.prepareSubscription(eq(USER_ID), eq(PlanType.PREMIUM), anyString(), eq(AMOUNT)))
                    .willReturn(subscription);

            // when
            SubscriptionPrepareResponse response = subscriptionService.prepareSubscription(USER_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.subscriptionKey()).isEqualTo(SUBSCRIPTION_KEY);
            assertThat(response.amount()).isEqualTo(AMOUNT);
            assertThat(response.planType()).isEqualTo(PlanType.PREMIUM);

            verify(subscriptionTransactionService, times(1)).validateNotSubscribed(USER_ID);
            verify(subscriptionTransactionService, times(1))
                    .prepareSubscription(eq(USER_ID), eq(PlanType.PREMIUM), anyString(), eq(AMOUNT));
        }

        @Test
        @DisplayName("실패 - 이미 활성 구독이 있으면 ERR_ALREADY_SUBSCRIBED")
        void prepareSubscription_alreadySubscribed_fail() {
            // given
            SubscriptionPrepareRequest request = new SubscriptionPrepareRequest(PlanType.PREMIUM, AMOUNT);

            doThrow(new ServiceErrorException(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED))
                    .when(subscriptionTransactionService).validateNotSubscribed(USER_ID);

            // when & then
            assertThatThrownBy(() -> subscriptionService.prepareSubscription(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessageContaining(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED.getMessage());

            verify(subscriptionTransactionService, times(1)).validateNotSubscribed(USER_ID);
            verify(subscriptionTransactionService, never())
                    .prepareSubscription(anyLong(), any(), anyString(), anyLong());
        }
    }

    // =========================================================
    // completeSubscription() - 구독 완료
    // =========================================================
    @Nested
    @DisplayName("completeSubscription() - 구독 완료")
    class CompleteSubscriptionTest {

        @Test
        @DisplayName("성공 - 빌링키로 첫 결제 + 구독 활성화")
        void completeSubscription_success() throws Exception {
            // given
            ReflectionTestUtils.setField(subscriptionService, "portoneApiSecret", PORTONE_API_SECRET);

            SubscriptionCompleteRequest request = new SubscriptionCompleteRequest(SUBSCRIPTION_KEY, BILLING_KEY);

            Subscription pendingSubscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.PENDING, SUBSCRIPTION_KEY, null);
            Subscription activeSubscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE, SUBSCRIPTION_KEY, BILLING_KEY);
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT);

            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(subscriptionTransactionService.getSubscriptionForComplete(USER_ID, SUBSCRIPTION_KEY))
                    .willReturn(pendingSubscription);
            doNothing().when(subscriptionTransactionService).validateSubscriptionStillPending(SUBSCRIPTION_ID);
            // POST 응답: TossPayments 실제 응답 형식 (status 없음)
            String portonePostResponse = "{\"payment\":{\"pgTxId\":\"test-tx-id\",\"paidAt\":\"2026-01-01T00:00:00Z\"}}";
            // GET 응답: 전체 Payment 객체 (status 포함)
            String portoneGetResponse = "{\"status\":\"PAID\",\"id\":\"payment-id\"}";
            given(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .willReturn(ResponseEntity.ok(portonePostResponse));
            given(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .willReturn(ResponseEntity.ok(portoneGetResponse));
            // ObjectMapper mock 설정 (GET 응답 바디 파싱)
            com.fasterxml.jackson.databind.JsonNode mockJsonNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
            com.fasterxml.jackson.databind.JsonNode mockStatusNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
            given(objectMapper.readTree(portoneGetResponse)).willReturn(mockJsonNode);
            given(mockJsonNode.path("status")).willReturn(mockStatusNode);
            given(mockStatusNode.asText()).willReturn("PAID");
            given(subscriptionTransactionService.createPaymentAndPurchase(eq(USER_ID), anyString(), eq(AMOUNT)))
                    .willReturn(payment);
            given(subscriptionTransactionService.completeSubscription(SUBSCRIPTION_KEY, BILLING_KEY, PAYMENT_ID))
                    .willReturn(activeSubscription);

            // when
            SubscriptionResponse response = subscriptionService.completeSubscription(USER_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(SUBSCRIPTION_ID);
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);

            verify(redisUtil, times(1)).acquireLock(anyString());
            verify(redisUtil, times(1)).releaseLock(anyString());
            verify(subscriptionTransactionService, times(1))
                    .getSubscriptionForComplete(USER_ID, SUBSCRIPTION_KEY);
            verify(restTemplate, times(1))
                    .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
            verify(restTemplate, times(1))
                    .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(subscriptionTransactionService, times(1))
                    .createPaymentAndPurchase(eq(USER_ID), anyString(), eq(AMOUNT));
            verify(subscriptionTransactionService, times(1))
                    .completeSubscription(SUBSCRIPTION_KEY, BILLING_KEY, PAYMENT_ID);
            verify(notificationProducer, times(1)).publish(argThat(e ->
                    e.userId().equals(USER_ID) && e.type() == NotificationType.SUBSCRIPTION_ACTIVATED));
        }

        @Test
        @DisplayName("실패 - Redis Lock 획득 실패하면 ERR_SUBSCRIPTION_PROCESSING")
        void completeSubscription_lockAcquireFailed_fail() {
            // given
            SubscriptionCompleteRequest request = new SubscriptionCompleteRequest(SUBSCRIPTION_KEY, BILLING_KEY);
            Subscription pendingSubscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.PENDING, SUBSCRIPTION_KEY, null);

            // Lock 전에 subscription 조회가 먼저 일어남
            given(subscriptionTransactionService.getSubscriptionForComplete(USER_ID, SUBSCRIPTION_KEY))
                    .willReturn(pendingSubscription);
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> subscriptionService.completeSubscription(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessageContaining(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_PROCESSING.getMessage());

            verify(subscriptionTransactionService, times(1)).getSubscriptionForComplete(USER_ID, SUBSCRIPTION_KEY);
            verify(redisUtil, times(1)).acquireLock(anyString());
            verify(redisUtil, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("실패 - PortOne 빌링키 결제 API 400 오류")
        void completeSubscription_portoneApiBadRequest_fail() {
            // given
            ReflectionTestUtils.setField(subscriptionService, "portoneApiSecret", PORTONE_API_SECRET);

            SubscriptionCompleteRequest request = new SubscriptionCompleteRequest(SUBSCRIPTION_KEY, BILLING_KEY);

            Subscription pendingSubscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.PENDING, SUBSCRIPTION_KEY, null);

            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(subscriptionTransactionService.getSubscriptionForComplete(USER_ID, SUBSCRIPTION_KEY))
                    .willReturn(pendingSubscription);
            given(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

            // when & then
            assertThatThrownBy(() -> subscriptionService.completeSubscription(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessageContaining(SubscriptionExceptionEnum.ERR_PORTONE_API_ERROR.getMessage());

            verify(redisUtil, times(1)).releaseLock(anyString());
        }
    }

    // =========================================================
    // cancelSubscription() - 구독 취소
    // =========================================================
    @Nested
    @DisplayName("cancelSubscription() - 구독 취소")
    class CancelSubscriptionTest {

        @Test
        @DisplayName("성공 - 빌링키 삭제 + 구독 취소")
        void cancelSubscription_success() {
            // given
            ReflectionTestUtils.setField(subscriptionService, "portoneApiSecret", PORTONE_API_SECRET);

            SubscriptionCancelRequest request = new SubscriptionCancelRequest("서비스 불만족");

            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE, SUBSCRIPTION_KEY, BILLING_KEY);

            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(subscriptionTransactionService.getSubscriptionForCancel(USER_ID, SUBSCRIPTION_ID))
                    .willReturn(subscription);
            given(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                    .willReturn(ResponseEntity.ok("{}"));
            given(subscriptionTransactionService.cancelSubscription(SUBSCRIPTION_ID))
                    .willReturn(subscription);

            // when
            subscriptionService.cancelSubscription(USER_ID, SUBSCRIPTION_ID, request);

            // then
            verify(redisUtil, times(1)).acquireLock(anyString());
            verify(redisUtil, times(1)).releaseLock(anyString());
            verify(subscriptionTransactionService, times(1))
                    .getSubscriptionForCancel(USER_ID, SUBSCRIPTION_ID);
            verify(restTemplate, times(1))
                    .exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class));
            verify(subscriptionTransactionService, times(1)).cancelSubscription(SUBSCRIPTION_ID);
            verify(notificationProducer, times(1)).publish(argThat(e ->
                    e.userId().equals(USER_ID) && e.type() == NotificationType.SUBSCRIPTION_CANCELLED));
        }

        @Test
        @DisplayName("성공 - 빌링키 삭제 실패해도 구독 취소는 진행")
        void cancelSubscription_billingKeyDeleteFailed_stillCancel() {
            // given
            ReflectionTestUtils.setField(subscriptionService, "portoneApiSecret", PORTONE_API_SECRET);

            SubscriptionCancelRequest request = new SubscriptionCancelRequest("서비스 불만족");

            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE, SUBSCRIPTION_KEY, BILLING_KEY);

            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(subscriptionTransactionService.getSubscriptionForCancel(USER_ID, SUBSCRIPTION_ID))
                    .willReturn(subscription);
            given(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));
            given(subscriptionTransactionService.cancelSubscription(SUBSCRIPTION_ID))
                    .willReturn(subscription);

            // when
            subscriptionService.cancelSubscription(USER_ID, SUBSCRIPTION_ID, request);

            // then
            verify(subscriptionTransactionService, times(1)).cancelSubscription(SUBSCRIPTION_ID);
        }

        @Test
        @DisplayName("실패 - Redis Lock 획득 실패하면 ERR_SUBSCRIPTION_PROCESSING")
        void cancelSubscription_lockAcquireFailed_fail() {
            // given
            SubscriptionCancelRequest request = new SubscriptionCancelRequest("서비스 불만족");

            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> subscriptionService.cancelSubscription(USER_ID, SUBSCRIPTION_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessageContaining(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_PROCESSING.getMessage());

            verify(subscriptionTransactionService, never()).getSubscriptionForCancel(anyLong(), anyLong());
        }
    }

    // =========================================================
    // getMySubscription() - 내 구독 조회
    // =========================================================
    @Nested
    @DisplayName("getMySubscription() - 내 구독 조회")
    class GetMySubscriptionTest {

        @Test
        @DisplayName("성공 - ACTIVE 구독 조회")
        void getMySubscription_success() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE, SUBSCRIPTION_KEY, BILLING_KEY);

            given(subscriptionTransactionService.getActiveSubscription(USER_ID))
                    .willReturn(Optional.of(subscription));

            // when
            SubscriptionResponse response = subscriptionService.getMySubscription(USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(SUBSCRIPTION_ID);
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);

            verify(subscriptionTransactionService, times(1)).getActiveSubscription(USER_ID);
        }

        @Test
        @DisplayName("실패 - 활성 구독이 없으면 ERR_SUBSCRIPTION_NOT_FOUND")
        void getMySubscription_notFound_fail() {
            // given
            given(subscriptionTransactionService.getActiveSubscription(USER_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> subscriptionService.getMySubscription(USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessageContaining(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND.getMessage());

            verify(subscriptionTransactionService, times(1)).getActiveSubscription(USER_ID);
        }
    }

    // =========================================================
    // processBillingForSubscription() - 정기 청구 처리 (스케줄러용)
    // =========================================================
    @Nested
    @DisplayName("processBillingForSubscription() - 정기 청구")
    class ProcessBillingForSubscriptionTest {

        @Test
        @DisplayName("성공 - 정기 청구 성공")
        void processBillingForSubscription_success() throws Exception {
            // given
            ReflectionTestUtils.setField(subscriptionService, "portoneApiSecret", PORTONE_API_SECRET);

            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE, SUBSCRIPTION_KEY, BILLING_KEY);
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT);

            given(redisUtil.acquireLock(anyString())).willReturn(true);
            // POST 응답: TossPayments 실제 응답 형식 (status 없음)
            String portonePostResponse = "{\"payment\":{\"pgTxId\":\"test-tx-id\",\"paidAt\":\"2026-01-01T00:00:00Z\"}}";
            // GET 응답: 전체 Payment 객체 (status 포함)
            String portoneGetResponse = "{\"status\":\"PAID\",\"id\":\"payment-id\"}";
            given(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .willReturn(ResponseEntity.ok(portonePostResponse));
            given(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .willReturn(ResponseEntity.ok(portoneGetResponse));
            // ObjectMapper mock 설정 (GET 응답 바디 파싱)
            com.fasterxml.jackson.databind.JsonNode mockJsonNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
            com.fasterxml.jackson.databind.JsonNode mockStatusNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
            given(objectMapper.readTree(portoneGetResponse)).willReturn(mockJsonNode);
            given(mockJsonNode.path("status")).willReturn(mockStatusNode);
            given(mockStatusNode.asText()).willReturn("PAID");
            given(subscriptionTransactionService.createPaymentAndPurchase(eq(USER_ID), anyString(), eq(AMOUNT)))
                    .willReturn(payment);
            doNothing().when(subscriptionTransactionService)
                    .updateSubscriptionAfterPayment(BILLING_KEY, PAYMENT_ID);

            // when
            subscriptionService.processBillingForSubscription(subscription);

            // then
            verify(redisUtil, times(1)).acquireLock(anyString());
            verify(redisUtil, times(1)).releaseLock(anyString());
            verify(restTemplate, times(1))
                    .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
            verify(subscriptionTransactionService, times(1))
                    .createPaymentAndPurchase(eq(USER_ID), anyString(), eq(AMOUNT));
            verify(subscriptionTransactionService, times(1))
                    .updateSubscriptionAfterPayment(BILLING_KEY, PAYMENT_ID);
        }

        @Test
        @DisplayName("실패 - 정기 청구 실패 시 구독 자동 취소")
        void processBillingForSubscription_paymentFailed_autoCancel() {
            // given
            ReflectionTestUtils.setField(subscriptionService, "portoneApiSecret", PORTONE_API_SECRET);

            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE, SUBSCRIPTION_KEY, BILLING_KEY);

            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));
            doNothing().when(subscriptionTransactionService)
                    .cancelSubscriptionDueToPaymentFailure(eq(SUBSCRIPTION_ID), anyString());

            // when
            subscriptionService.processBillingForSubscription(subscription);

            // then
            verify(subscriptionTransactionService, times(1))
                    .cancelSubscriptionDueToPaymentFailure(eq(SUBSCRIPTION_ID), anyString());
            verify(redisUtil, times(1)).releaseLock(anyString());
        }

        @Test
        @DisplayName("실패 - Redis Lock 획득 실패하면 처리 스킵")
        void processBillingForSubscription_lockAcquireFailed_skip() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE, SUBSCRIPTION_KEY, BILLING_KEY);

            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when
            subscriptionService.processBillingForSubscription(subscription);

            // then
            verify(redisUtil, times(1)).acquireLock(anyString());
            verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
            verify(subscriptionTransactionService, never()).createPaymentAndPurchase(anyLong(), anyString(), anyLong());
        }
    }
}
