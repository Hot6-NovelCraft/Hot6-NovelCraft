package com.example.hot6novelcraft.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;

// 탈퇴 후 계정 복구 DTO
public record UserRestoreRequest(

        @NotNull(message = "복구 토큰은 필수 입니다.")
        String recoveryToken
) {
}
