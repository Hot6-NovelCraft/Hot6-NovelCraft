package com.example.hot6novelcraft.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SocialSignupRequest(

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
    public SocialSignupRequest {
        if (phoneNo != null) {
            phoneNo = phoneNo.replaceAll("-", "");
        }
    }
}
