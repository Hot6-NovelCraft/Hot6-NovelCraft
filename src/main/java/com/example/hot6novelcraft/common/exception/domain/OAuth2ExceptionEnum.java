package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum OAuth2ExceptionEnum implements ErrorCode {

    ERR_NOT_FOUND_USER(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다"),
    ERR_NOT_FOUND_SOCIAL_ACCOUNT(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다"),
    ERR_UNSUPPORTED_PROVIDER(HttpStatus.UNAUTHORIZED, "지원하지 않는 소셜 플랫폼입니다");

    private final HttpStatus httpStatus;
    private final String message;

    OAuth2ExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
