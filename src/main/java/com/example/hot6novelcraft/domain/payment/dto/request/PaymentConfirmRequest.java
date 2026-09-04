package com.example.hot6novelcraft.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 포트원 V2 결제 확인 요청
 * 프론트에서 PortOne.requestPayment() 완료 후 전송
 */
public record PaymentConfirmRequest(
        @NotBlank(message = "paymentId는 필수입니다")
        String paymentId,   // 프론트에서 직접 생성한 주문 ID (포트원 V2 paymentId)

        @NotNull(message = "결제 금액은 필수입니다")
        @Positive(message = "결제 금액은 0보다 커야 합니다")
        Long amount
) {
}
