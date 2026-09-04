package com.example.hot6novelcraft.domain.subscription.dto.response;

import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.example.hot6novelcraft.domain.subscription.entity.enums.PlanType;
import com.example.hot6novelcraft.domain.subscription.entity.enums.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionResponse(
        Long id,
        Long userId,
        PlanType planType,
        SubscriptionStatus status,
        Long amount,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime nextBillingAt,
        String failReason
) {
    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getUserId(),
                subscription.getPlanType(),
                subscription.getSubscriptionStatus(),
                subscription.getAmount(),
                subscription.getStartedAt(),
                subscription.getEndedAt(),
                subscription.getNextBillingAt(),
                subscription.getFailReason()
        );
    }
}
