package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SearchExceptionEnum implements ErrorCode {

    ERR_SEARCH_KEYWORD_EMPTY(HttpStatus.BAD_REQUEST, "keyword는 비어 있을 수 없습니다"),
    ERR_SIGN_IN_SERVICE(HttpStatus.BAD_REQUEST, "로그인이 필요한 서비스입니다");

    private final HttpStatus httpStatus;
    private final String message;

    SearchExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
