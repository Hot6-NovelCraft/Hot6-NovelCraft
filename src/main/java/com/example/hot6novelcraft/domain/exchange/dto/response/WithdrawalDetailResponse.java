package com.example.hot6novelcraft.domain.exchange.dto.response;

import com.example.hot6novelcraft.domain.exchange.entity.Withdrawal;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;

import java.time.LocalDateTime;

public record WithdrawalDetailResponse(
        Long withdrawalId,
        Integer requestAmount,
        Integer fee,
        Integer actualAmount,
        WithdrawalStatus status,
        String rejectedReason,
        LocalDateTime requestedAt,
        LocalDateTime processedAt,
        BankAccountInfoResponse bankAccount
) {
    public static WithdrawalDetailResponse of(Withdrawal withdrawal, BankAccountInfoResponse bankAccount) {
        return new WithdrawalDetailResponse(
                withdrawal.getId(),
                withdrawal.getRequestAmount(),
                withdrawal.getFee(),
                withdrawal.getActualAmount(),
                withdrawal.getStatus(),
                withdrawal.getRejectedReason(),
                withdrawal.getRequestedAt(),
                withdrawal.getProcessedAt(),
                bankAccount
        );
    }
}