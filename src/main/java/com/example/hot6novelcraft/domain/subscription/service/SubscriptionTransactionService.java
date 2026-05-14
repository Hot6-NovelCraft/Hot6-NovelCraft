package com.example.hot6novelcraft.domain.subscription.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.SubscriptionExceptionEnum;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.payment.repository.PaymentRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.example.hot6novelcraft.domain.purchases.entity.Purchase;
import com.example.hot6novelcraft.domain.purchases.entity.enums.PurchaseType;
import com.example.hot6novelcraft.domain.purchases.repository.PurchaseRepository;
import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.example.hot6novelcraft.domain.subscription.entity.enums.PlanType;
import com.example.hot6novelcraft.domain.subscription.entity.enums.SubscriptionStatus;
import com.example.hot6novelcraft.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionTransactionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final PointService pointService;

    @Transactional(readOnly = true)
    public void validateNotSubscribed(Long userId) {
        // ACTIVE 구독 체크
        Optional<Subscription> activeSubscription = subscriptionRepository
                .findByUserIdAndSubscriptionStatus(userId, SubscriptionStatus.ACTIVE);

        if (activeSubscription.isPresent()) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED);
        }

        // PENDING 구독 체크 (중복 prepare 방지)
        Optional<Subscription> pendingSubscription = subscriptionRepository
                .findByUserIdAndSubscriptionStatus(userId, SubscriptionStatus.PENDING);

        if (pendingSubscription.isPresent()) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED);
        }
    }

    @Transactional
    public Subscription prepareSubscription(Long userId, PlanType planType, String subscriptionKey, Long amount) {
        // 취소됐지만 기간이 남은 구독이 있으면 새 row 생성 대신 재활성화
        Optional<Subscription> cancelled = subscriptionRepository
                .findFirstByUserIdAndSubscriptionStatusAndNextBillingAtAfterOrderByNextBillingAtDesc(
                        userId, SubscriptionStatus.CANCELLED, LocalDateTime.now());

        if (cancelled.isPresent()) {
            Subscription sub = cancelled.get();
            sub.reactivate(subscriptionKey);
            return subscriptionRepository.save(sub);
        }

        Subscription subscription = Subscription.prepare(userId, planType, subscriptionKey, amount);
        return subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public Subscription getSubscriptionForComplete(Long userId, String subscriptionKey) {
        Subscription subscription = subscriptionRepository.findBySubscriptionKey(subscriptionKey)
                .orElseThrow(() -> new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_KEY_NOT_FOUND));

        // 소유자 검증
        if (!subscription.getUserId().equals(userId)) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_NOT_MY_SUBSCRIPTION);
        }

        // PENDING 상태 확인
        if (subscription.getSubscriptionStatus() != SubscriptionStatus.PENDING) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_INVALID_SUBSCRIPTION_STATUS);
        }

        return subscription;
    }

    @Transactional
    public Subscription completeSubscription(String subscriptionKey, String billingKey, Long firstPaymentId) {
        Subscription subscription = subscriptionRepository.findBySubscriptionKey(subscriptionKey)
                .orElseThrow(() -> new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_KEY_NOT_FOUND));

        // 🔒 Race condition 방어: complete 시점에 이미 ACTIVE 구독이 있는지 재확인
        // (여러 PENDING이 동시에 complete를 시도하는 경우 방어)
        Optional<Subscription> existingActive = subscriptionRepository
                .findByUserIdAndSubscriptionStatus(subscription.getUserId(), SubscriptionStatus.ACTIVE);

        if (existingActive.isPresent() && !existingActive.get().getId().equals(subscription.getId())) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED);
        }

        subscription.complete(billingKey, firstPaymentId);
        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public Payment createPayment(Long userId, String paymentKey, Long amount) {
        Payment payment = Payment.create(userId, paymentKey, amount, null);
        payment.complete(null);  // COMPLETED 상태로 전환
        return paymentRepository.save(payment);
    }

    @Transactional
    public void createPurchase(Long userId, Long amount, Long paymentId) {
        Purchase purchase = Purchase.create(userId, PurchaseType.SUBSCRIPTION, amount, paymentId);
        purchaseRepository.save(purchase);
    }

    /**
     * Payment와 Purchase를 단일 트랜잭션으로 생성
     * 원자성 보장: 둘 다 성공하거나 둘 다 롤백
     */
    @Transactional
    public Payment createPaymentAndPurchase(Long userId, String paymentKey, Long amount) {
        // Payment 생성
        Payment payment = Payment.create(userId, paymentKey, amount, null);
        payment.complete(null);  // COMPLETED 상태로 전환
        Payment savedPayment = paymentRepository.save(payment);

        // Purchase 생성 (같은 트랜잭션)
        Purchase purchase = Purchase.create(userId, PurchaseType.SUBSCRIPTION, amount, savedPayment.getId());
        purchaseRepository.save(purchase);

        return savedPayment;
    }

    @Transactional(readOnly = true)
    public Subscription getSubscriptionForCancel(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND));

        // 소유자 검증
        if (!subscription.getUserId().equals(userId)) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_NOT_MY_SUBSCRIPTION);
        }

        // 이미 취소된 구독인지 확인
        if (subscription.getSubscriptionStatus() == SubscriptionStatus.CANCELLED) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_ALREADY_CANCELLED);
        }

        return subscription;
    }

    @Transactional
    public Subscription cancelSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND));

        subscription.cancel();
        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public void cancelSubscriptionDueToPaymentFailure(Long subscriptionId, String reason) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND));

        subscription.markPaymentFailed(reason);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void updateSubscriptionAfterPayment(String billingKey, Long paymentId) {
        Subscription subscription = subscriptionRepository.findByBillingKey(billingKey)
                .orElseThrow(() -> new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND));

        subscription.updateAfterPayment(paymentId);
        subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> getActiveSubscription(Long userId) {
        return subscriptionRepository.findCurrentSubscription(userId, LocalDateTime.now());
    }

    /**
     * Lock 획득 후 Subscription이 여전히 PENDING 상태인지 재검증
     * (Lock 획득 전~후 사이에 상태가 변경되었을 가능성 방어)
     */
    @Transactional(readOnly = true)
    public void validateSubscriptionStillPending(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND));

        if (subscription.getSubscriptionStatus() != SubscriptionStatus.PENDING) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_INVALID_SUBSCRIPTION_STATUS);
        }
    }
}
