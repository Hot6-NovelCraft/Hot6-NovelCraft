package com.example.hot6novelcraft.domain.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 결제창 열기 전 서버에 결제 준비를 요청하는 DTO.
 * 서버가 paymentKey를 미리 생성하고 PENDING Payment를 저장하여
 * 토큰 만료 시에도 웹훅으로 포인트 충전이 가능하도록 한다.
 */
public record PaymentPrepareRequest(
        @NotNull(message = "결제 금액은 필수입니다")
        @Positive(message = "결제 금액은 0보다 커야 합니다")
        Long amount
) {
}
