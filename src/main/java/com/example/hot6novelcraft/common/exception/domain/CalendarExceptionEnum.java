package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CalendarExceptionEnum implements ErrorCode {

    READING_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "독서 기록을 찾을 수 없습니다"),
    INVALID_READ_PAGE(HttpStatus.BAD_REQUEST, "읽은 페이지 수는 음수일 수 없습니다"),
    BOOK_NOT_IN_LIBRARY(HttpStatus.NOT_FOUND, "서재에 등록되지 않은 도서입니다"),
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "잘못된 날짜 형식입니다"),
    INVALID_STAT_DATE(HttpStatus.BAD_REQUEST, "유효하지 않은 날짜 범위입니다"),
    DATE_RANGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "조회 범위가 너무 큽니다");  // CAL-4002 추가

    private final HttpStatus httpStatus;
    private final String message;

    CalendarExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
