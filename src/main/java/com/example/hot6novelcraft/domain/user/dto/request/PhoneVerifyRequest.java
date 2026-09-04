package com.example.hot6novelcraft.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PhoneVerifyRequest(

        @NotBlank
        @Pattern(regexp = "^010\\d{7,8}$", message = "유효하지 않은 휴대폰 번호 형식입니다.")
        String phoneNo,

        @NotBlank
        @Size(min = 6, max = 6, message = "6자리의 인증번호를 입력해주세요")
        String verificationCode

) {

    // 번호 입력시 하이픈 "-" 이 있을 시, 오류 반환 -> 하이픈 제거
    public PhoneVerifyRequest {
        if (phoneNo != null) {
            phoneNo = phoneNo.replaceAll("-", "");
        }
    }

}

