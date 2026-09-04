package com.example.hot6novelcraft.domain.exchange.exception;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExchangeExceptionEnum implements ErrorCode {

    // 계좌 관련
    BANK_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 계좌를 찾을 수 없습니다"),
    BANK_ACCOUNT_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "이미 인증이 완료된 계좌입니다"),
    BANK_ACCOUNT_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "계좌 인증이 완료되지 않았습니다"),
    BANK_ACCOUNT_DUPLICATE(HttpStatus.CONFLICT, "이미 등록된 계좌번호입니다"),

    // 1원 인증 관련
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "인증 요청 정보를 찾을 수 없습니다"),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증 유효시간이 만료되었습니다"),
    VERIFICATION_MAX_ATTEMPT_EXCEEDED(HttpStatus.BAD_REQUEST, "인증 시도 횟수를 초과하였습니다"),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다"),
    VERIFICATION_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "이미 인증이 완료된 요청입니다"),

    // 환전 관련
    WITHDRAWAL_INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "출금 가능한 잔액이 부족합니다"),
    WITHDRAWAL_BELOW_MINIMUM(HttpStatus.BAD_REQUEST, "최소 환전 금액 이상부터 신청 가능합니다"),
    WITHDRAWAL_PENDING_EXISTS(HttpStatus.CONFLICT, "처리 대기 중인 환전 신청이 존재합니다"),
    WITHDRAWAL_NOT_FOUND(HttpStatus.NOT_FOUND, "환전 내역을 찾을 수 없습니다"),
    WITHDRAWAL_INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "환전 상태를 변경할 수 없습니다"),
    WITHDRAWAL_NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 환전 내역만 조회할 수 있습니다"),

    // 외부 API 관련
    BANK_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "은행 서비스 점검 시간입니다"),
    BANK_API_CALL_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "은행 API 호출에 실패했습니다"),
    BANK_ACCOUNT_HOLDER_MISMATCH(HttpStatus.BAD_REQUEST, "예금주 정보가 일치하지 않습니다");

    private final HttpStatus httpStatus;
    private final String message;
}