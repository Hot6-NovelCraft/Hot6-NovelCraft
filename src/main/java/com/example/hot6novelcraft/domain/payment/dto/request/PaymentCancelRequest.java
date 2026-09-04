package com.example.hot6novelcraft.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentCancelRequest(
        @NotBlank(message = "환불 사유는 필수입니다")
        String reason
) {
}
