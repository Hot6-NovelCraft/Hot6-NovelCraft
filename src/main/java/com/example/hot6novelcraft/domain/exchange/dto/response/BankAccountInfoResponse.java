package com.example.hot6novelcraft.domain.exchange.dto.response;

public record BankAccountInfoResponse(
        Long bankAccountId,
        String bankName,
        String maskedAccountNumber,
        String accountHolder,
        Boolean isVerified
) {
    public static BankAccountInfoResponse of(
            Long bankAccountId,
            String bankName,
            String maskedAccountNumber,
            String accountHolder,
            Boolean isVerified
    ) {
        return new BankAccountInfoResponse(bankAccountId, bankName, maskedAccountNumber, accountHolder, isVerified);
    }
}