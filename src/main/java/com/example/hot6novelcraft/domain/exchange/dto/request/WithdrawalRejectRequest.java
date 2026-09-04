package com.example.hot6novelcraft.domain.exchange.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawalRejectRequest(
        @NotBlank(message = "거절 사유를 입력해 주세요")
        @Size(max = 255, message = "거절 사유는 255자 이내로 입력해 주세요")
        String rejectedReason
) {
}