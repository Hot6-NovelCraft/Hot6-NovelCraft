package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum RecommendationAiExceptionEnum implements ErrorCode {

    NOT_FOUND_JSON_ARRAY(HttpStatus.NOT_FOUND, "JSON 배열을 찾을 수 없습니다");

    private final HttpStatus httpStatus;
    private final String message;

    RecommendationAiExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
