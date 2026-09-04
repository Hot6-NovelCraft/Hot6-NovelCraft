package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatExceptionEnum implements ErrorCode {

    ERR_MENTORSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "멘토십을 찾을 수 없습니다"),
    ERR_CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다"),
    ERR_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닙니다"),
    ERR_WEBSOCKET_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "WebSocket 인증에 실패했습니다"),
    ERR_NOT_AUTHOR(HttpStatus.FORBIDDEN, "채팅은 작가(AUTHOR) 간에만 가능합니다"),
    ERR_MENTOR_NOT_FOUND(HttpStatus.NOT_FOUND, "멘토 정보를 찾을 수 없습니다"),
    ERR_INVALID_MESSAGE(HttpStatus.BAD_REQUEST, "메시지 내용과 타입은 필수입니다");

    private final HttpStatus httpStatus;
    private final String message;
}
