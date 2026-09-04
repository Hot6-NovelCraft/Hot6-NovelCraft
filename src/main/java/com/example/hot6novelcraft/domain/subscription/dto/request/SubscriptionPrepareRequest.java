package com.example.hot6novelcraft.domain.subscription.dto.request;

import com.example.hot6novelcraft.domain.subscription.entity.enums.PlanType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubscriptionPrepareRequest(
        @NotNull(message = "플랜 타입은 필수입니다")
        PlanType planType,

        @NotNull(message = "구독 금액은 필수입니다")
        @Min(value = 1, message = "구독 금액은 1원 이상이어야 합니다")
        Long amount
) {
}
