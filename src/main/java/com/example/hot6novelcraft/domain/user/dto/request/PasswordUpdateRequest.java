package com.example.hot6novelcraft.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(

        @NotBlank String oldPassword,

        @NotBlank(message = "비밀번호 입력은 필수입니다.")
        @Size(min = 8)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "비밀번호는 8자 이상, 영문자와 숫자를 포함 해야합니다.")
        String newPassword
) {
}
