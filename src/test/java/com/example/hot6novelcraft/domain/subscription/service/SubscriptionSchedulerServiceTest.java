package com.example.hot6novelcraft.domain.subscription.service;

import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.example.hot6novelcraft.domain.subscription.entity.enums.PlanType;
import com.example.hot6novelcraft.domain.subscription.entity.enums.SubscriptionStatus;
import com.example.hot6novelcraft.domain.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionSchedulerService 테스트")
class SubscriptionSchedulerServiceTest {

    @InjectMocks
    private SubscriptionSchedulerService schedulerService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionService subscriptionService;

    private static final Long USER_ID_1 = 1L;
    private static final Long USER_ID_2 = 2L;
    private static final Long SUBSCRIPTION_ID_1 = 100L;
    private static final Long SUBSCRIPTION_ID_2 = 101L;
    private static final Long AMOUNT = 9900L;
    private static final String BILLING_KEY_1 = "billing-key-1";
    private static final String BILLING_KEY_2 = "billing-key-2";
    private static final String SUBSCRIPTION_KEY_1 = "subscription-key-1";
    private static final String SUBSCRIPTION_KEY_2 = "subscription-key-2";

    private Subscription createMockSubscription(Long id, Long userId, SubscriptionStatus status,
                                                PlanType planType, String billingKey, Long amount,
                                                LocalDateTime nextBillingAt) {
        Subscription subscription = mock(Subscription.class);
        given(subscription.getId()).willReturn(id);
        given(subscription.getUserId()).willReturn(userId);
        given(subscription.getSubscriptionStatus()).willReturn(status);
        given(subscription.getPlanType()).willReturn(planType);
        given(subscription.getBillingKey()).willReturn(billingKey);
        given(subscription.getAmount()).willReturn(amount);
        given(subscription.getNextBillingAt()).willReturn(nextBillingAt);
        return subscription;
    }

    // =========================================================
    // processMonthlyBilling() - 정기 청구 스케줄러
    // =========================================================
    @Nested
    @DisplayName("processMonthlyBilling() - 정기 청구")
    class ProcessMonthlyBillingTest {

        @Test
        @DisplayName("성공 - 청구 대상이 없으면 아무것도 하지 않음")
        void processMonthlyBilling_noDueSubscriptions_success() {
            // given
            given(subscriptionRepository.findAllBySubscriptionStatusAndNextBillingAtBefore(
                    eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            schedulerService.processMonthlyBilling();

            // then
            verify(subscriptionRepository, times(1))
                    .findAllBySubscriptionStatusAndNextBillingAtBefore(
                            eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class));
            verify(subscriptionService, never()).processBillingForSubscription(any());
        }

        @Test
        @DisplayName("성공 - 청구 대상 1건 처리")
        void processMonthlyBilling_oneDueSubscription_success() {
            // given
            Subscription subscription1 = createMockSubscription(
                    SUBSCRIPTION_ID_1, USER_ID_1, SubscriptionStatus.ACTIVE,
                    PlanType.PREMIUM, BILLING_KEY_1, AMOUNT, LocalDateTime.now().minusDays(1)
            );

            given(subscriptionRepository.findAllBySubscriptionStatusAndNextBillingAtBefore(
                    eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                    .willReturn(List.of(subscription1));
            doNothing().when(subscriptionService).processBillingForSubscription(subscription1);

            // when
            schedulerService.processMonthlyBilling();

            // then
            verify(subscriptionRepository, times(1))
                    .findAllBySubscriptionStatusAndNextBillingAtBefore(
                            eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class));
            verify(subscriptionService, times(1)).processBillingForSubscription(subscription1);
        }

        @Test
        @DisplayName("성공 - 청구 대상 여러 건 처리")
        void processMonthlyBilling_multipleDueSubscriptions_success() {
            // given
            Subscription subscription1 = createMockSubscription(
                    SUBSCRIPTION_ID_1, USER_ID_1, SubscriptionStatus.ACTIVE,
                    PlanType.PREMIUM, BILLING_KEY_1, AMOUNT, LocalDateTime.now().minusDays(1)
            );

            Subscription subscription2 = createMockSubscription(
                    SUBSCRIPTION_ID_2, USER_ID_2, SubscriptionStatus.ACTIVE,
                    PlanType.PREMIUM, BILLING_KEY_2, 4900L, LocalDateTime.now().minusDays(2)
            );

            given(subscriptionRepository.findAllBySubscriptionStatusAndNextBillingAtBefore(
                    eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                    .willReturn(Arrays.asList(subscription1, subscription2));
            doNothing().when(subscriptionService).processBillingForSubscription(subscription1);
            doNothing().when(subscriptionService).processBillingForSubscription(subscription2);

            // when
            schedulerService.processMonthlyBilling();

            // then
            verify(subscriptionRepository, times(1))
                    .findAllBySubscriptionStatusAndNextBillingAtBefore(
                            eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class));
            verify(subscriptionService, times(1)).processBillingForSubscription(subscription1);
            verify(subscriptionService, times(1)).processBillingForSubscription(subscription2);
        }

        @Test
        @DisplayName("부분 성공 - 일부 청구 실패해도 계속 진행")
        void processMonthlyBilling_partialFailure_continueProcessing() {
            // given
            Subscription subscription1 = createMockSubscription(
                    SUBSCRIPTION_ID_1, USER_ID_1, SubscriptionStatus.ACTIVE,
                    PlanType.PREMIUM, BILLING_KEY_1, AMOUNT, LocalDateTime.now().minusDays(1)
            );

            Subscription subscription2 = createMockSubscription(
                    SUBSCRIPTION_ID_2, USER_ID_2, SubscriptionStatus.ACTIVE,
                    PlanType.PREMIUM, BILLING_KEY_2, 4900L, LocalDateTime.now().minusDays(2)
            );

            given(subscriptionRepository.findAllBySubscriptionStatusAndNextBillingAtBefore(
                    eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                    .willReturn(Arrays.asList(subscription1, subscription2));

            // subscription1 청구 실패
            doThrow(new RuntimeException("Payment failed"))
                    .when(subscriptionService).processBillingForSubscription(subscription1);

            // subscription2 청구 성공
            doNothing().when(subscriptionService).processBillingForSubscription(subscription2);

            // when
            schedulerService.processMonthlyBilling();

            // then
            verify(subscriptionService, times(1)).processBillingForSubscription(subscription1);
            verify(subscriptionService, times(1)).processBillingForSubscription(subscription2);
        }
    }

    // =========================================================
    // cleanUpPendingSubscriptions() - PENDING 구독 정리
    // =========================================================
    @Nested
    @DisplayName("cleanUpPendingSubscriptions() - PENDING 구독 정리")
    class CleanUpPendingSubscriptionsTest {

        @Test
        @DisplayName("성공 - 삭제 대상이 없으면 아무것도 하지 않음")
        void cleanUpPendingSubscriptions_noExpiredSubscriptions_success() {
            // given
            given(subscriptionRepository.findAllBySubscriptionStatusAndCreatedAtBefore(
                    eq(SubscriptionStatus.PENDING), any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            schedulerService.cleanUpPendingSubscriptions();

            // then
            verify(subscriptionRepository, times(1))
                    .findAllBySubscriptionStatusAndCreatedAtBefore(
                            eq(SubscriptionStatus.PENDING), any(LocalDateTime.class));
            verify(subscriptionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("성공 - 24시간 이상 PENDING 구독 1건 삭제")
        void cleanUpPendingSubscriptions_oneExpiredSubscription_success() {
            // given
            Subscription expiredSubscription = createMockSubscription(
                    SUBSCRIPTION_ID_1, USER_ID_1, SubscriptionStatus.PENDING,
                    PlanType.PREMIUM, null, AMOUNT, null
            );

            given(subscriptionRepository.findAllBySubscriptionStatusAndCreatedAtBefore(
                    eq(SubscriptionStatus.PENDING), any(LocalDateTime.class)))
                    .willReturn(List.of(expiredSubscription));
            doNothing().when(subscriptionRepository).delete(expiredSubscription);

            // when
            schedulerService.cleanUpPendingSubscriptions();

            // then
            verify(subscriptionRepository, times(1))
                    .findAllBySubscriptionStatusAndCreatedAtBefore(
                            eq(SubscriptionStatus.PENDING), any(LocalDateTime.class));
            verify(subscriptionRepository, times(1)).delete(expiredSubscription);
        }

        @Test
        @DisplayName("성공 - 24시간 이상 PENDING 구독 여러 건 삭제")
        void cleanUpPendingSubscriptions_multipleExpiredSubscriptions_success() {
            // given
            Subscription expiredSubscription1 = createMockSubscription(
                    SUBSCRIPTION_ID_1, USER_ID_1, SubscriptionStatus.PENDING,
                    PlanType.PREMIUM, null, AMOUNT, null
            );

            Subscription expiredSubscription2 = createMockSubscription(
                    SUBSCRIPTION_ID_2, USER_ID_2, SubscriptionStatus.PENDING,
                    PlanType.PREMIUM, null, 4900L, null
            );

            given(subscriptionRepository.findAllBySubscriptionStatusAndCreatedAtBefore(
                    eq(SubscriptionStatus.PENDING), any(LocalDateTime.class)))
                    .willReturn(Arrays.asList(expiredSubscription1, expiredSubscription2));
            doNothing().when(subscriptionRepository).delete(expiredSubscription1);
            doNothing().when(subscriptionRepository).delete(expiredSubscription2);

            // when
            schedulerService.cleanUpPendingSubscriptions();

            // then
            verify(subscriptionRepository, times(1))
                    .findAllBySubscriptionStatusAndCreatedAtBefore(
                            eq(SubscriptionStatus.PENDING), any(LocalDateTime.class));
            verify(subscriptionRepository, times(1)).delete(expiredSubscription1);
            verify(subscriptionRepository, times(1)).delete(expiredSubscription2);
        }

        @Test
        @DisplayName("부분 성공 - 일부 삭제 실패해도 계속 진행")
        void cleanUpPendingSubscriptions_partialFailure_continueProcessing() {
            // given
            Subscription expiredSubscription1 = createMockSubscription(
                    SUBSCRIPTION_ID_1, USER_ID_1, SubscriptionStatus.PENDING,
                    PlanType.PREMIUM, null, AMOUNT, null
            );

            Subscription expiredSubscription2 = createMockSubscription(
                    SUBSCRIPTION_ID_2, USER_ID_2, SubscriptionStatus.PENDING,
                    PlanType.PREMIUM, null, 4900L, null
            );

            given(subscriptionRepository.findAllBySubscriptionStatusAndCreatedAtBefore(
                    eq(SubscriptionStatus.PENDING), any(LocalDateTime.class)))
                    .willReturn(Arrays.asList(expiredSubscription1, expiredSubscription2));

            // expiredSubscription1 삭제 실패
            doThrow(new RuntimeException("Delete failed"))
                    .when(subscriptionRepository).delete(expiredSubscription1);

            // expiredSubscription2 삭제 성공
            doNothing().when(subscriptionRepository).delete(expiredSubscription2);

            // when
            schedulerService.cleanUpPendingSubscriptions();

            // then
            verify(subscriptionRepository, times(1)).delete(expiredSubscription1);
            verify(subscriptionRepository, times(1)).delete(expiredSubscription2);
        }
    }
}
