package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum MentorExceptionEnum implements ErrorCode {

    MENTOR_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 멘토 프로필을 찾을 수 없습니다"),
    MENTOR_PENDING_EXISTS(HttpStatus.CONFLICT, "이미 심사 중인 멘토 신청이 있습니다"),
    MENTOR_ALREADY_APPROVED(HttpStatus.CONFLICT, "이미 승인된 멘토 계정이 존재합니다"),
    MENTOR_ALREADY_PROCESSED(HttpStatus.BAD_REQUEST, "이미 승인되거나 거절된 멘토 계정입니다"),
    MENTOR_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 멘토 프로필만 수정할 수 있습니다"),
    MENTOR_CAREER_REQUIRED(HttpStatus.BAD_REQUEST, "경력 사항 필드는 필수 입력 값입니다"),
    MENTOR_BIO_TOO_SHORT(HttpStatus.BAD_REQUEST, "멘토 소개는 최소 10자 이상 입력해 주세요"),
    MENTOR_MAX_MENTEES_EXCEEDED(HttpStatus.BAD_REQUEST, "최대 멘티 수는 5명을 초과할 수 없습니다"),
    MENTOR_FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드 중 오류가 발생했습니다"),
    MENTOR_JSON_SERIALIZE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "데이터 처리 중 오류가 발생했습니다");

    private final HttpStatus httpStatus;
    private final String message;

    MentorExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}