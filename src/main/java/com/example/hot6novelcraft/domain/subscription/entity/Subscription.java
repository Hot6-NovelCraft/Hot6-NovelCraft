package com.example.hot6novelcraft.domain.subscription.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.subscription.entity.enums.PlanType;
import com.example.hot6novelcraft.domain.subscription.entity.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "Subscriptions")
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private PlanType planType;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private SubscriptionStatus subscriptionStatus;

    private String failReason;

    @Column(unique = true)
    private String subscriptionKey;  // 서버 생성 고유 키 (prepare 시 발급)

    @Column(unique = true)
    private String billingKey;  // PortOne 빌링키 (complete 시 설정)

    private Long amount;  // 월 구독료

    private Long lastPaymentId;  // FK to Payment (ID만 보유)

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private LocalDateTime nextBillingAt;

    // PENDING 상태로 준비
    public static Subscription prepare(Long userId, PlanType planType, String subscriptionKey, Long amount) {
        Subscription subscription = new Subscription();
        subscription.userId = userId;
        subscription.planType = planType;
        subscription.subscriptionKey = subscriptionKey;
        subscription.amount = amount;
        subscription.subscriptionStatus = SubscriptionStatus.PENDING;
        subscription.startedAt = LocalDateTime.now();  // PENDING 상태에서도 생성 시간 기록
        return subscription;
    }

    // 첫 결제 완료 후 ACTIVE 전환
    public void complete(String billingKey, Long firstPaymentId) {
        this.billingKey = billingKey;
        this.lastPaymentId = firstPaymentId;
        this.subscriptionStatus = SubscriptionStatus.ACTIVE;
        this.startedAt = LocalDateTime.now();  // ACTIVE 전환 시점을 실제 구독 시작으로 기록
        this.nextBillingAt = LocalDateTime.now().plusMonths(1);
    }

    // 구독 취소
    public void cancel() {
        this.subscriptionStatus = SubscriptionStatus.CANCELLED;
        this.endedAt = LocalDateTime.now();
    }

    // 취소된 구독 재활성화 준비 — 새 billingKey는 complete 단계에서 주입
    public void reactivate(String newSubscriptionKey) {
        this.subscriptionKey = newSubscriptionKey;
        this.subscriptionStatus = SubscriptionStatus.PENDING;
        this.billingKey = null;
        this.endedAt = null;
        this.failReason = null;
    }

    // 정기 결제 후 업데이트
    // 멱등성 보장: 같은 paymentId면 스킵 (웹훅 재전송 대응)
    public void updateAfterPayment(Long paymentId) {
        if (this.lastPaymentId != null && this.lastPaymentId.equals(paymentId)) {
            // 이미 처리된 결제 - 웹훅 재전송이므로 스킵
            return;
        }
        this.lastPaymentId = paymentId;
        this.nextBillingAt = LocalDateTime.now().plusMonths(1);
    }

    // 결제 실패 표시
    public void markPaymentFailed(String reason) {
        this.failReason = reason;
        this.subscriptionStatus = SubscriptionStatus.CANCELLED;
        this.endedAt = LocalDateTime.now();
    }
}
