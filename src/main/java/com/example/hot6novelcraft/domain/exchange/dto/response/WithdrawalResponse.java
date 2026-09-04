package com.example.hot6novelcraft.domain.exchange.dto.response;

import com.example.hot6novelcraft.domain.exchange.entity.Withdrawal;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;

import java.time.LocalDateTime;

public record WithdrawalResponse(
        Long withdrawalId,
        Integer requestAmount,
        Integer fee,
        Integer actualAmount,
        WithdrawalStatus status,
        String rejectedReason,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
    public static WithdrawalResponse from(Withdrawal withdrawal) {
        return new WithdrawalResponse(
                withdrawal.getId(),
                withdrawal.getRequestAmount(),
                withdrawal.getFee(),
                withdrawal.getActualAmount(),
                withdrawal.getStatus(),
                withdrawal.getRejectedReason(),
                withdrawal.getRequestedAt(),
                withdrawal.getProcessedAt()
        );
    }
}