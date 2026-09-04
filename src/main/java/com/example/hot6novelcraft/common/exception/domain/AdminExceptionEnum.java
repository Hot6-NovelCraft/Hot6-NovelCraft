package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AdminExceptionEnum implements ErrorCode {

    NOT_FOUND_DATA_AT_DATE(HttpStatus.NOT_FOUND, "해당 날짜의 데이터가 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    AdminExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

}
