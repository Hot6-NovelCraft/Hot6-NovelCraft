package com.example.hot6novelcraft.domain.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionCompleteRequest(
        @NotBlank(message = "구독 키는 필수입니다")
        String subscriptionKey,

        @NotBlank(message = "빌링 키는 필수입니다")
        String billingKey
) {
}
