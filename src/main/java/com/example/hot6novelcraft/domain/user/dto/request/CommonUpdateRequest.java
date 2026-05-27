package com.example.hot6novelcraft.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CommonUpdateRequest(

        @Size(min = 1, max = 10, message = "10자이내의 닉네임 및 필명을 입력해주세요.")
        String nickname,

        @Pattern(regexp = "^010\\d{8}$", message = "유효하지 않은 휴대폰 번호 형식입니다.")
        String phoneNo

) {

    public CommonUpdateRequest {
        if (phoneNo != null) {
            phoneNo = phoneNo.replaceAll("-", "");
        }
    }
}
