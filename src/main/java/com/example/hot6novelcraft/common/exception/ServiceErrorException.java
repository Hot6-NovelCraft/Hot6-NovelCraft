package com.example.hot6novelcraft.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ServiceErrorException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final ErrorCode errorCode;

    public ServiceErrorException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.httpStatus = errorCode.getHttpStatus();
        this.errorCode = errorCode;
    }
}
