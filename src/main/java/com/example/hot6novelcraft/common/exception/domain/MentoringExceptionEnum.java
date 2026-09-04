package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MentoringExceptionEnum implements ErrorCode {

    MENTORING_NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 멘토링 정보를 찾을 수 없습니다"),
    MENTORING_ALREADY_PROCESSED(HttpStatus.BAD_REQUEST, "이미 처리된 멘토링 요청입니다"),
    MENTORING_UNAUTHORIZED(HttpStatus.FORBIDDEN, "해당 멘토링에 대한 권한이 없습니다"),
    MENTORING_MENTEE_NOT_MATCH(HttpStatus.BAD_REQUEST, "해당 멘토링의 멘티 정보가 일치하지 않습니다"),
    MENTORING_SLOT_FULL(HttpStatus.BAD_REQUEST, "멘토링 정원이 마감되었습니다"),
    MENTORING_MANUSCRIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부된 원고 파일을 찾을 수 없습니다"),
    MENTORING_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "진행 중인 멘토링만 종료할 수 있습니다"),
    MENTORING_FEEDBACK_ONLY_ACCEPTED(HttpStatus.BAD_REQUEST, "진행 중인 멘토링에만 피드백을 작성할 수 있습니다"),
    MENTORING_SESSION_CONFLICT(HttpStatus.CONFLICT, "동시 요청으로 인해 피드백 등록에 실패했습니다 잠시 후 다시 시도해 주세요"),

    MENTORING_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 진행 중인 멘토링이 있습니다"),
    MENTORING_SELF_APPLY(HttpStatus.BAD_REQUEST, "본인에게 멘토링을 신청할 수 없습니다"),
    MENTORING_NOT_AUTHOR(HttpStatus.FORBIDDEN, "작가 권한이 필요합니다"),
    MENTORING_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "완료된 멘토링만 평가할 수 있습니다"),
    MENTORING_REVIEW_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 평가한 멘토링입니다"),
    MENTORING_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "대기 중인 신청만 취소할 수 있습니다");


    private final HttpStatus httpStatus;
    private final String message;
}