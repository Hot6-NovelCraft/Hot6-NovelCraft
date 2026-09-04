package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EventExceptionEnum implements ErrorCode {

    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 이벤트를 찾을 수 없습니다"),
    EVENT_NOT_ONGOING(HttpStatus.BAD_REQUEST, "현재 참여 가능한 이벤트가 아닙니다"),
    EVENT_ALREADY_PARTICIPATED(HttpStatus.CONFLICT, "이미 참여한 이벤트입니다"),
    EVENT_PARTICIPANTS_FULL(HttpStatus.CONFLICT, "이벤트 참여 인원이 마감되었습니다"),
    EVENT_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "이벤트 시작일은 종료일보다 이전이어야 합니다"),
    EVENT_LOCK_ACQUIRE_FAILED(HttpStatus.TOO_MANY_REQUESTS, "현재 요청이 많아 처리할 수 없습니다. 잠시 후 다시 시도해 주세요");

    private final HttpStatus httpStatus;
    private final String message;
}