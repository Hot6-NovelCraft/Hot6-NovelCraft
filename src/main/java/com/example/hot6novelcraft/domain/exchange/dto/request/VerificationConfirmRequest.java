package com.example.hot6novelcraft.domain.exchange.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerificationConfirmRequest(
        @NotNull(message = "계좌 ID를 입력해 주세요")
        Long bankAccountId,

        @NotBlank(message = "인증 코드를 입력해 주세요")
        @Pattern(regexp = "^\\d{4}$", message = "인증 코드는 4자리 숫자여야 합니다")
        String verificationCode
) {
}