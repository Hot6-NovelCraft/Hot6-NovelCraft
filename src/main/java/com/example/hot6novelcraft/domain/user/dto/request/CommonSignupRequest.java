package com.example.hot6novelcraft.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CommonSignupRequest(

        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @NotBlank(message = "이메일 입력은 필수 입니다.")
        String email,

        @NotBlank(message = "비밀번호 입력은 필수입니다.")
        @Size(min = 8)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "비밀번호는 8자 이상, 영문자와 숫자를 포함 해야합니다.")
        String password,

        @NotBlank(message = "닉네임 및 필명 입력은 필수입니다.")
        @Size(min = 1, max = 10, message = "10자이내의 닉네임 및 필명을 입력해주세요.")
        String nickname,

        @NotNull
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate birthDay,

        @NotBlank(message = "휴대폰번호 입력은 필수입니다.")
        @Pattern(regexp = "^010\\d{7,8}$", message = "유효하지 않은 휴대폰 번호 형식입니다.")
        String phoneNo,

        @NotBlank
        String tempToken

) {
    public CommonSignupRequest {
        if (phoneNo != null) {
            phoneNo = phoneNo.replaceAll("-", "");
        }
    }

}
