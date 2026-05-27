package com.example.hot6novelcraft.domain.user.entity.enums;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.OAuth2ExceptionEnum;
import lombok.Getter;

@Getter
public enum ProviderSns {
    GOOGLE, KAKAO, NAVER;

    public static ProviderSns from(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> GOOGLE;
            case "kakao" -> KAKAO;
            case "naver" -> NAVER;
            default -> throw new ServiceErrorException(OAuth2ExceptionEnum.ERR_UNSUPPORTED_PROVIDER);
        };
    }
}
