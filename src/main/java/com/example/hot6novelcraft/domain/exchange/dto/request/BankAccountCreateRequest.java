package com.example.hot6novelcraft.domain.exchange.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BankAccountCreateRequest(
        @NotBlank(message = "은행명을 입력해 주세요")
        String bankName,

        @NotBlank(message = "계좌번호를 입력해 주세요")
        @Pattern(regexp = "^\\d{7,16}$", message = "계좌번호는 7~16자리 숫자여야 합니다")
        String accountNumber,

        @NotBlank(message = "예금주를 입력해 주세요")
        String accountHolder
) {
}