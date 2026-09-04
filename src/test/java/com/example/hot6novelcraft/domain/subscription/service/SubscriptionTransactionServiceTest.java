package com.example.hot6novelcraft.domain.subscription.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.SubscriptionExceptionEnum;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.payment.repository.PaymentRepository;
import com.example.hot6novelcraft.domain.purchases.entity.Purchase;
import com.example.hot6novelcraft.domain.purchases.entity.enums.PurchaseType;
import com.example.hot6novelcraft.domain.purchases.repository.PurchaseRepository;
import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.example.hot6novelcraft.domain.subscription.entity.enums.PlanType;
import com.example.hot6novelcraft.domain.subscription.entity.enums.SubscriptionStatus;
import com.example.hot6novelcraft.domain.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionTransactionService 테스트")
class SubscriptionTransactionServiceTest {

    @InjectMocks
    private SubscriptionTransactionService transactionService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    private static final Long USER_ID = 1L;
    private static final Long SUBSCRIPTION_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final String SUBSCRIPTION_KEY = "sub-key-12345";
    private static final String BILLING_KEY = "billing-key-12345";
    private static final String PAYMENT_KEY = "payment-key-12345";
    private static final Long AMOUNT = 9900L;

    private Subscription createMockSubscription(Long id, Long userId, SubscriptionStatus status) {
        Subscription subscription = mock(Subscription.class);
        given(subscription.getId()).willReturn(id);
        given(subscription.getUserId()).willReturn(userId);
        given(subscription.getSubscriptionStatus()).willReturn(status);
        given(subscription.getSubscriptionKey()).willReturn(SUBSCRIPTION_KEY);
        given(subscription.getBillingKey()).willReturn(BILLING_KEY);
        given(subscription.getAmount()).willReturn(AMOUNT);
        return subscription;
    }

    private Payment createMockPayment(Long id, Long userId, PaymentStatus status) {
        Payment payment = mock(Payment.class);
        given(payment.getId()).willReturn(id);
        given(payment.getUserId()).willReturn(userId);
        given(payment.getAmount()).willReturn(AMOUNT);
        given(payment.getStatus()).willReturn(status);
        given(payment.getPaymentKey()).willReturn(PAYMENT_KEY);
        return payment;
    }

    // =========================================================
    // validateNotSubscribed() - 중복 구독 검증
    // =========================================================
    @Nested
    @DisplayName("validateNotSubscribed() - 중복 구독 검증")
    class ValidateNotSubscribedTest {

        @Test
        @DisplayName("성공 - ACTIVE와 PENDING 구독이 모두 없으면 통과")
        void validateNotSubscribed_noActiveOrPendingSubscription_success() {
            // given
            given(subscriptionRepository.findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE))
                    .willReturn(Optional.empty());
            given(subscriptionRepository.findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.PENDING))
                    .willReturn(Optional.empty());

            // when & then (예외 없어야 함)
            transactionService.validateNotSubscribed(USER_ID);

            verify(subscriptionRepository, times(1))
                    .findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE);
            verify(subscriptionRepository, times(1))
                    .findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.PENDING);
        }

        @Test
        @DisplayName("실패 - ACTIVE 구독이 있으면 ERR_ALREADY_SUBSCRIBED")
        void validateNotSubscribed_activeSubscriptionExists_throwsException() {
            // given
            Subscription activeSubscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE);
            given(subscriptionRepository.findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE))
                    .willReturn(Optional.of(activeSubscription));

            // when & then
            assertThatThrownBy(() -> transactionService.validateNotSubscribed(USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED.getMessage());

            verify(subscriptionRepository, times(1))
                    .findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE);
            verify(subscriptionRepository, never())
                    .findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.PENDING);
        }

        @Test
        @DisplayName("실패 - PENDING 구독이 있으면 ERR_ALREADY_SUBSCRIBED")
        void validateNotSubscribed_pendingSubscriptionExists_throwsException() {
            // given
            Subscription pendingSubscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.PENDING);
            given(subscriptionRepository.findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE))
                    .willReturn(Optional.empty());
            given(subscriptionRepository.findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.PENDING))
                    .willReturn(Optional.of(pendingSubscription));

            // when & then
            assertThatThrownBy(() -> transactionService.validateNotSubscribed(USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED.getMessage());

            verify(subscriptionRepository, times(1))
                    .findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE);
            verify(subscriptionRepository, times(1))
                    .findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.PENDING);
        }
    }

    // =========================================================
    // prepareSubscription() - 구독 준비
    // =========================================================
    @Nested
    @DisplayName("prepareSubscription() - 구독 준비")
    class PrepareSubscriptionTest {

        @Test
        @DisplayName("성공 - PENDING Subscription 생성 및 저장")
        void prepareSubscription_success() {
            // given
            Subscription newSubscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.PENDING);
            given(subscriptionRepository.save(any(Subscription.class))).willReturn(newSubscription);

            // when
            Subscription result = transactionService.prepareSubscription(
                    USER_ID, PlanType.PREMIUM, SUBSCRIPTION_KEY, AMOUNT
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(SUBSCRIPTION_ID);
            verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        }
    }

    // =========================================================
    // getSubscriptionForComplete() - 구독 완료용 조회
    // =========================================================
    @Nested
    @DisplayName("getSubscriptionForComplete() - 구독 완료용 조회")
    class GetSubscriptionForCompleteTest {

        @Test
        @DisplayName("성공 - 올바른 PENDING Subscription 조회")
        void getSubscriptionForComplete_validPendingSubscription_success() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.PENDING);
            given(subscriptionRepository.findBySubscriptionKey(SUBSCRIPTION_KEY))
                    .willReturn(Optional.of(subscription));

            // when
            Subscription result = transactionService.getSubscriptionForComplete(USER_ID, SUBSCRIPTION_KEY);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(SUBSCRIPTION_ID);
        }

        @Test
        @DisplayName("실패 - subscriptionKey가 없으면 ERR_SUBSCRIPTION_KEY_NOT_FOUND")
        void getSubscriptionForComplete_subscriptionKeyNotFound_throwsException() {
            // given
            given(subscriptionRepository.findBySubscriptionKey(SUBSCRIPTION_KEY))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.getSubscriptionForComplete(USER_ID, SUBSCRIPTION_KEY))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_KEY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 다른 사용자의 구독이면 ERR_NOT_MY_SUBSCRIPTION")
        void getSubscriptionForComplete_notMySubscription_throwsException() {
            // given
            Long otherUserId = 999L;
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, otherUserId, SubscriptionStatus.PENDING);
            given(subscriptionRepository.findBySubscriptionKey(SUBSCRIPTION_KEY))
                    .willReturn(Optional.of(subscription));

            // when & then
            assertThatThrownBy(() -> transactionService.getSubscriptionForComplete(USER_ID, SUBSCRIPTION_KEY))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_NOT_MY_SUBSCRIPTION.getMessage());
        }

        @Test
        @DisplayName("실패 - PENDING 상태가 아니면 ERR_INVALID_SUBSCRIPTION_STATUS")
        void getSubscriptionForComplete_notPendingStatus_throwsException() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE);
            given(subscriptionRepository.findBySubscriptionKey(SUBSCRIPTION_KEY))
                    .willReturn(Optional.of(subscription));

            // when & then
            assertThatThrownBy(() -> transactionService.getSubscriptionForComplete(USER_ID, SUBSCRIPTION_KEY))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_INVALID_SUBSCRIPTION_STATUS.getMessage());
        }
    }

    // =========================================================
    // completeSubscription() - 구독 완료
    // =========================================================
    @Nested
    @DisplayName("completeSubscription() - 구독 완료")
    class CompleteSubscriptionTest {

        @Test
        @DisplayName("성공 - Subscription ACTIVE 전환 및 저장")
        void completeSubscription_success() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.PENDING);
            given(subscriptionRepository.findBySubscriptionKey(SUBSCRIPTION_KEY))
                    .willReturn(Optional.of(subscription));
            // Race condition 방어: ACTIVE 구독 없음 확인
            given(subscriptionRepository.findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE))
                    .willReturn(Optional.empty());
            given(subscriptionRepository.save(any(Subscription.class))).willReturn(subscription);

            // when
            Subscription result = transactionService.completeSubscription(
                    SUBSCRIPTION_KEY, BILLING_KEY, PAYMENT_ID
            );

            // then
            assertThat(result).isNotNull();
            verify(subscription, times(1)).complete(BILLING_KEY, PAYMENT_ID);
            verify(subscriptionRepository, times(1)).save(subscription);
            verify(subscriptionRepository, times(1))
                    .findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("실패 - subscriptionKey가 없으면 ERR_SUBSCRIPTION_KEY_NOT_FOUND")
        void completeSubscription_subscriptionKeyNotFound_throwsException() {
            // given
            given(subscriptionRepository.findBySubscriptionKey(SUBSCRIPTION_KEY))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.completeSubscription(
                    SUBSCRIPTION_KEY, BILLING_KEY, PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_KEY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 이미 ACTIVE 구독이 있으면 ERR_ALREADY_SUBSCRIBED (Race condition 방어)")
        void completeSubscription_activeSubscriptionAlreadyExists_throwsException() {
            // given
            Long existingSubscriptionId = 999L;
            Subscription pendingSubscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.PENDING);
            Subscription existingActiveSubscription = createMockSubscription(existingSubscriptionId, USER_ID, SubscriptionStatus.ACTIVE);

            given(subscriptionRepository.findBySubscriptionKey(SUBSCRIPTION_KEY))
                    .willReturn(Optional.of(pendingSubscription));
            // Race condition: 다른 PENDING이 먼저 ACTIVE로 전환됨
            given(subscriptionRepository.findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE))
                    .willReturn(Optional.of(existingActiveSubscription));

            // when & then
            assertThatThrownBy(() -> transactionService.completeSubscription(
                    SUBSCRIPTION_KEY, BILLING_KEY, PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED.getMessage());

            verify(subscriptionRepository, times(1))
                    .findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE);
            verify(pendingSubscription, never()).complete(any(), any());
            verify(subscriptionRepository, never()).save(pendingSubscription);
        }
    }

    // =========================================================
    // createPayment() - Payment 생성
    // =========================================================
    @Nested
    @DisplayName("createPayment() - Payment 생성")
    class CreatePaymentTest {

        @Test
        @DisplayName("성공 - COMPLETED 상태의 Payment 생성")
        void createPayment_success() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.COMPLETED);
            given(paymentRepository.save(any(Payment.class))).willReturn(payment);

            // when
            Payment result = transactionService.createPayment(USER_ID, PAYMENT_KEY, AMOUNT);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(PAYMENT_ID);
            verify(paymentRepository, times(1)).save(any(Payment.class));
        }
    }

    // =========================================================
    // createPurchase() - Purchase 생성
    // =========================================================
    @Nested
    @DisplayName("createPurchase() - Purchase 생성")
    class CreatePurchaseTest {

        @Test
        @DisplayName("성공 - SUBSCRIPTION 타입 Purchase 생성")
        void createPurchase_success() {
            // given
            Purchase mockPurchase = mock(Purchase.class);
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.createPurchase(USER_ID, AMOUNT, PAYMENT_ID);

            // then
            ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
            verify(purchaseRepository).save(captor.capture());

            Purchase captured = captor.getValue();
            assertThat(captured.getUserId()).isEqualTo(USER_ID);
            assertThat(captured.getType()).isEqualTo(PurchaseType.SUBSCRIPTION);
            assertThat(captured.getAmount()).isEqualTo(AMOUNT);
            assertThat(captured.getPaymentId()).isEqualTo(PAYMENT_ID);
        }
    }

    // =========================================================
    // createPaymentAndPurchase() - Payment와 Purchase를 단일 트랜잭션으로 생성
    // =========================================================
    @Nested
    @DisplayName("createPaymentAndPurchase() - Payment와 Purchase 통합 생성")
    class CreatePaymentAndPurchaseTest {

        @Test
        @DisplayName("성공 - Payment와 Purchase가 단일 트랜잭션으로 생성됨")
        void createPaymentAndPurchase_success() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.COMPLETED);
            given(paymentRepository.save(any(Payment.class))).willReturn(mockPayment);
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mock(Purchase.class));

            // when
            Payment result = transactionService.createPaymentAndPurchase(USER_ID, PAYMENT_KEY, AMOUNT);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(PAYMENT_ID);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getAmount()).isEqualTo(AMOUNT);

            // Payment와 Purchase 모두 저장되었는지 검증
            verify(paymentRepository, times(1)).save(any(Payment.class));
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
        }
    }

    // =========================================================
    // getSubscriptionForCancel() - 구독 취소용 조회
    // =========================================================
    @Nested
    @DisplayName("getSubscriptionForCancel() - 구독 취소용 조회")
    class GetSubscriptionForCancelTest {

        @Test
        @DisplayName("성공 - 취소 가능한 구독 조회")
        void getSubscriptionForCancel_validSubscription_success() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE);
            given(subscriptionRepository.findById(SUBSCRIPTION_ID))
                    .willReturn(Optional.of(subscription));

            // when
            Subscription result = transactionService.getSubscriptionForCancel(USER_ID, SUBSCRIPTION_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(SUBSCRIPTION_ID);
        }

        @Test
        @DisplayName("실패 - 구독이 없으면 ERR_SUBSCRIPTION_NOT_FOUND")
        void getSubscriptionForCancel_subscriptionNotFound_throwsException() {
            // given
            given(subscriptionRepository.findById(SUBSCRIPTION_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.getSubscriptionForCancel(USER_ID, SUBSCRIPTION_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 다른 사용자의 구독이면 ERR_NOT_MY_SUBSCRIPTION")
        void getSubscriptionForCancel_notMySubscription_throwsException() {
            // given
            Long otherUserId = 999L;
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, otherUserId, SubscriptionStatus.ACTIVE);
            given(subscriptionRepository.findById(SUBSCRIPTION_ID))
                    .willReturn(Optional.of(subscription));

            // when & then
            assertThatThrownBy(() -> transactionService.getSubscriptionForCancel(USER_ID, SUBSCRIPTION_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_NOT_MY_SUBSCRIPTION.getMessage());
        }

        @Test
        @DisplayName("실패 - 이미 취소된 구독이면 ERR_ALREADY_CANCELLED")
        void getSubscriptionForCancel_alreadyCancelled_throwsException() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.CANCELLED);
            given(subscriptionRepository.findById(SUBSCRIPTION_ID))
                    .willReturn(Optional.of(subscription));

            // when & then
            assertThatThrownBy(() -> transactionService.getSubscriptionForCancel(USER_ID, SUBSCRIPTION_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_ALREADY_CANCELLED.getMessage());
        }
    }

    // =========================================================
    // cancelSubscription() - 구독 취소
    // =========================================================
    @Nested
    @DisplayName("cancelSubscription() - 구독 취소")
    class CancelSubscriptionTest {

        @Test
        @DisplayName("성공 - Subscription CANCELLED 전환 및 저장")
        void cancelSubscription_success() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE);
            given(subscriptionRepository.findById(SUBSCRIPTION_ID))
                    .willReturn(Optional.of(subscription));
            given(subscriptionRepository.save(any(Subscription.class))).willReturn(subscription);

            // when
            Subscription result = transactionService.cancelSubscription(SUBSCRIPTION_ID);

            // then
            assertThat(result).isNotNull();
            verify(subscription, times(1)).cancel();
            verify(subscriptionRepository, times(1)).save(subscription);
        }

        @Test
        @DisplayName("실패 - 구독이 없으면 ERR_SUBSCRIPTION_NOT_FOUND")
        void cancelSubscription_subscriptionNotFound_throwsException() {
            // given
            given(subscriptionRepository.findById(SUBSCRIPTION_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.cancelSubscription(SUBSCRIPTION_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND.getMessage());
        }
    }

    // =========================================================
    // cancelSubscriptionDueToPaymentFailure() - 결제 실패로 인한 구독 취소
    // =========================================================
    @Nested
    @DisplayName("cancelSubscriptionDueToPaymentFailure() - 결제 실패로 인한 구독 취소")
    class CancelSubscriptionDueToPaymentFailureTest {

        @Test
        @DisplayName("성공 - 구독 상태를 실패로 마킹하고 저장")
        void cancelSubscriptionDueToPaymentFailure_success() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE);
            String reason = "정기 결제 실패: 카드 한도 초과";
            given(subscriptionRepository.findById(SUBSCRIPTION_ID))
                    .willReturn(Optional.of(subscription));
            given(subscriptionRepository.save(any(Subscription.class))).willReturn(subscription);

            // when
            transactionService.cancelSubscriptionDueToPaymentFailure(SUBSCRIPTION_ID, reason);

            // then
            verify(subscription, times(1)).markPaymentFailed(reason);
            verify(subscriptionRepository, times(1)).save(subscription);
        }

        @Test
        @DisplayName("실패 - 구독이 없으면 ERR_SUBSCRIPTION_NOT_FOUND")
        void cancelSubscriptionDueToPaymentFailure_subscriptionNotFound_throwsException() {
            // given
            given(subscriptionRepository.findById(SUBSCRIPTION_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.cancelSubscriptionDueToPaymentFailure(
                    SUBSCRIPTION_ID, "Test reason"))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND.getMessage());
        }
    }

    // =========================================================
    // updateSubscriptionAfterPayment() - 정기 결제 후 구독 업데이트
    // =========================================================
    @Nested
    @DisplayName("updateSubscriptionAfterPayment() - 정기 결제 후 구독 업데이트")
    class UpdateSubscriptionAfterPaymentTest {

        @Test
        @DisplayName("성공 - 구독 업데이트 및 저장")
        void updateSubscriptionAfterPayment_success() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE);
            given(subscriptionRepository.findByBillingKey(BILLING_KEY))
                    .willReturn(Optional.of(subscription));
            given(subscriptionRepository.save(any(Subscription.class))).willReturn(subscription);

            // when
            transactionService.updateSubscriptionAfterPayment(BILLING_KEY, PAYMENT_ID);

            // then
            verify(subscription, times(1)).updateAfterPayment(PAYMENT_ID);
            verify(subscriptionRepository, times(1)).save(subscription);
        }

        @Test
        @DisplayName("실패 - billingKey로 구독을 찾을 수 없으면 ERR_SUBSCRIPTION_NOT_FOUND")
        void updateSubscriptionAfterPayment_subscriptionNotFound_throwsException() {
            // given
            given(subscriptionRepository.findByBillingKey(BILLING_KEY))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.updateSubscriptionAfterPayment(BILLING_KEY, PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND.getMessage());
        }
    }

    // =========================================================
    // getActiveSubscription() - ACTIVE 구독 조회
    // =========================================================
    @Nested
    @DisplayName("getActiveSubscription() - ACTIVE 구독 조회")
    class GetActiveSubscriptionTest {

        @Test
        @DisplayName("성공 - ACTIVE 구독이 있으면 반환")
        void getActiveSubscription_activeSubscriptionExists_returnsSubscription() {
            // given
            Subscription subscription = createMockSubscription(SUBSCRIPTION_ID, USER_ID, SubscriptionStatus.ACTIVE);
            given(subscriptionRepository.findCurrentSubscription(eq(USER_ID), any(LocalDateTime.class)))
                    .willReturn(Optional.of(subscription));

            // when
            Optional<Subscription> result = transactionService.getActiveSubscription(USER_ID);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(SUBSCRIPTION_ID);
        }

        @Test
        @DisplayName("성공 - ACTIVE 구독이 없으면 빈 Optional 반환")
        void getActiveSubscription_noActiveSubscription_returnsEmpty() {
            // given
            given(subscriptionRepository.findByUserIdAndSubscriptionStatus(USER_ID, SubscriptionStatus.ACTIVE))
                    .willReturn(Optional.empty());

            // when
            Optional<Subscription> result = transactionService.getActiveSubscription(USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }
}
