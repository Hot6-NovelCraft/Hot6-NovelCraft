package com.example.hot6novelcraft.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneSendRequest(

        @NotBlank(message = "휴대폰번호 입력은 필수입니다.")
        @Pattern(regexp = "^010\\d{7,8}$", message = "유효하지 않은 휴대폰 번호 형식입니다.")
        String phoneNo
) {
    // 번호 입력시 하이픈 "-" 이 있을 시, 오류 반환 -> 하이픈 제거
    public PhoneSendRequest {
        if (phoneNo != null) {
            phoneNo = phoneNo.replaceAll("-", "");
        }
    }

}
