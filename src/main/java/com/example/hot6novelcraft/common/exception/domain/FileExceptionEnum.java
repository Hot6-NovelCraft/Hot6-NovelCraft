package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FileExceptionEnum implements ErrorCode {

    ERR_FILE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다"),
    ERR_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 너무 큽니다 (최대 10MB)"),
    ERR_FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다"),
    ERR_FILE_EMPTY(HttpStatus.BAD_REQUEST, "파일이 비어있습니다"),
    ERR_PRESIGNED_URL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "원고 다운로드 URL 발급에 실패했습니다");


    private final HttpStatus httpStatus;
    private final String message;
}