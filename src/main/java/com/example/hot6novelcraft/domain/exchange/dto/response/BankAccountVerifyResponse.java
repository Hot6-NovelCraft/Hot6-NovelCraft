package com.example.hot6novelcraft.domain.exchange.dto.response;

import com.example.hot6novelcraft.domain.exchange.entity.BankAccount;

import java.time.LocalDateTime;

public record BankAccountVerifyResponse(
        Long bankAccountId,
        String bankName,
        String maskedAccountNumber,
        String accountHolder,
        LocalDateTime expiredAt
) {
    public static BankAccountVerifyResponse of(BankAccount bankAccount, String maskedNumber, LocalDateTime expiredAt) {
        return new BankAccountVerifyResponse(
                bankAccount.getId(),
                bankAccount.getBankName(),
                maskedNumber,
                bankAccount.getAccountHolder(),
                expiredAt
        );
    }
}