package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum NotificationExceptionEnum implements ErrorCode {
    ERR_NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다"),
    ERR_NOT_MY_NOTIFICATION(HttpStatus.FORBIDDEN, "본인의 알림만 조회할 수 있습니다");

    private final HttpStatus httpStatus;
    private final String message;

    NotificationExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
