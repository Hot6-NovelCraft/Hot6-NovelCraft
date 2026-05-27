package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LibraryExceptionEnum implements ErrorCode {

    ALREADY_IN_LIBRARY(HttpStatus.CONFLICT, "이미 서재에 담긴 소설입니다"),
    NOVEL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 소설입니다"),
    LIBRARY_NOT_FOUND(HttpStatus.NOT_FOUND, "서재에서 해당 소설을 찾을 수 없습니다"),
    UNAUTHORIZED_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");

    private final HttpStatus httpStatus;
    private final String message;
}