package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SubscriptionExceptionEnum implements ErrorCode {
    ERR_ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "이미 활성 구독이 존재합니다"),
    ERR_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 정보를 찾을 수 없습니다"),
    ERR_NOT_MY_SUBSCRIPTION(HttpStatus.FORBIDDEN, "본인의 구독이 아닙니다"),
    ERR_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 구독입니다"),
    ERR_FIRST_PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "첫 결제 처리에 실패했습니다"),
    ERR_BILLING_KEY_ISSUE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "빌링키 발급에 실패했습니다"),
    ERR_SUBSCRIPTION_PROCESSING(HttpStatus.CONFLICT, "구독 처리가 이미 진행 중입니다"),
    ERR_PORTONE_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "결제 서버 오류가 발생했습니다"),
    ERR_INVALID_SUBSCRIPTION_STATUS(HttpStatus.CONFLICT, "잘못된 구독 상태입니다"),
    ERR_SUBSCRIPTION_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 키를 찾을 수 없습니다"),
    ERR_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "구독 금액이 플랜 정가와 일치하지 않습니다");

    private final HttpStatus httpStatus;
    private final String message;

    SubscriptionExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
