package com.example.hot6novelcraft.domain.payment.dto.response;

import com.example.hot6novelcraft.domain.payment.entity.Payment;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        Long amount,
        String status,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getCreatedAt()
        );
    }
}
