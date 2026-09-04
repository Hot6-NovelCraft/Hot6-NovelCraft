package com.example.hot6novelcraft.domain.subscription.dto.response;

import com.example.hot6novelcraft.domain.subscription.entity.enums.PlanType;

public record SubscriptionPrepareResponse(
        String subscriptionKey,
        Long amount,
        PlanType planType
) {
}
