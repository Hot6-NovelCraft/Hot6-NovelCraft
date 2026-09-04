package com.example.hot6novelcraft.domain.exchange.dto.response;

public record VerificationConfirmResponse(
        Long bankAccountId,
        Boolean isVerified,
        Integer remainingAttempts
) {
    public static VerificationConfirmResponse success(Long bankAccountId) {
        return new VerificationConfirmResponse(bankAccountId, true, null);
    }

    public static VerificationConfirmResponse fail(Long bankAccountId, Integer remainingAttempts) {
        return new VerificationConfirmResponse(bankAccountId, false, remainingAttempts);
    }
}