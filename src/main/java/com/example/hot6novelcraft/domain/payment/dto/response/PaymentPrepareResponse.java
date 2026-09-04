package com.example.hot6novelcraft.domain.payment.dto.response;

/**
 * 결제 준비 응답 DTO.
 * 서버가 생성한 paymentKey를 프론트에 전달한다.
 * 프론트는 이 paymentKey로 PortOne SDK 결제창을 열고,
 * 결제 완료 후 /confirm 호출 시에도 동일한 paymentKey를 사용한다.
 */
public record PaymentPrepareResponse(
        String paymentKey,
        Long amount
) {
}
