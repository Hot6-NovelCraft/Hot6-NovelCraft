package com.example.hot6novelcraft.domain.payment.dto.response;

import com.example.hot6novelcraft.domain.payment.entity.Payment;

import java.time.LocalDateTime;

public record PaymentHistoryResponse(
        Long paymentId,
        String paymentKey,
        Long amount,
        String method,
        String status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt
) {
    public static PaymentHistoryResponse from(Payment payment) {
        return new PaymentHistoryResponse(
                payment.getId(),
                payment.getPaymentKey(),
                payment.getAmount(),
                payment.getMethod() != null ? payment.getMethod().name() : null,
                payment.getStatus().name(),
                payment.getCreatedAt(),
                payment.getCancelledAt()
        );
    }
}