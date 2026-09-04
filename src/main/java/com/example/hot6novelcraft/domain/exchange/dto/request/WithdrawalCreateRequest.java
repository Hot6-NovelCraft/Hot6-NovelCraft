package com.example.hot6novelcraft.domain.exchange.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WithdrawalCreateRequest(
        @NotNull(message = "환전 금액을 입력해 주세요")
        @Min(value = 1, message = "환전 금액은 1원 이상이어야 합니다")
        Integer requestAmount
) {
}