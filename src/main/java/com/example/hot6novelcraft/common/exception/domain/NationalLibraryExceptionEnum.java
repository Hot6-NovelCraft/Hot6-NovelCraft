package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NationalLibraryExceptionEnum implements ErrorCode {

    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 도서를 찾을 수 없습니다"),
    BOOK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 저장된 도서입니다"),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "국립중앙도서관 API 호출 중 오류가 발생했습니다"),
    INVALID_ISBN(HttpStatus.BAD_REQUEST, "유효하지 않은 ISBN입니다"),
    BOOK_ALREADY_IN_SHELF(HttpStatus.CONFLICT, "이미 내 서재에 저장된 도서입니다"),
    BOOK_NOT_IN_SHELF(HttpStatus.NOT_FOUND, "내 서재에 없는 도서입니다");


    private final HttpStatus httpStatus;
    private final String message;
}