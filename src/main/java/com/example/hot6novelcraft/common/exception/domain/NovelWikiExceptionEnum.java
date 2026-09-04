package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum NovelWikiExceptionEnum implements ErrorCode {

    WIKI_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 설정집을 찾을 수 없습니다."),
    WIKI_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 소설의 설정집만 저장/조회/삭제를 할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    NovelWikiExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}