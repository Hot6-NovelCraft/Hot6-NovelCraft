package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CoverExceptionEnum implements ErrorCode {

    IMAGE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 생성에 실패했습니다"),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다"),
    NOVEL_NOT_FOUND(HttpStatus.NOT_FOUND, "소설을 찾을 수 없습니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
    NOT_AUTHOR(HttpStatus.FORBIDDEN, "작가만 사용할 수 있습니다"),
    NOT_NOVEL_OWNER(HttpStatus.FORBIDDEN, "본인의 소설만 표지를 생성할 수 있습니다"),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "표지 생성 작업을 찾을 수 없습니다");


    private final HttpStatus httpStatus;
    private final String message;

    public ServiceErrorException toException() {
        return new ServiceErrorException(this);
    }
}