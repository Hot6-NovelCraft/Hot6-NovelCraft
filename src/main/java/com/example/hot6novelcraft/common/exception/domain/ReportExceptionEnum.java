package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ReportExceptionEnum implements ErrorCode {

    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 신고 내역을 찾을 수 없습니다."),
    REPORT_SELF_FORBIDDEN(HttpStatus.BAD_REQUEST, "본인을 신고할 수 없습니다."),
    REPORT_DUPLICATE(HttpStatus.CONFLICT, "24시간 내에 동일 대상에 대한 신고 내역이 존재합니다."),
    REPORT_COMMENT_ID_REQUIRED(HttpStatus.BAD_REQUEST, "댓글 신고 시 댓글 ID는 필수입니다."),
    REPORT_COMMENT_ID_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "유저 신고 시 댓글 ID는 포함할 수 없습니다."),
    REPORT_TARGET_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 대상 유저를 찾을 수 없습니다."),
    REPORT_TARGET_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 대상 댓글을 찾을 수 없습니다."),
    REPORT_COMMENT_USER_MISMATCH(HttpStatus.BAD_REQUEST, "신고 대상 유저와 댓글 작성자가 일치하지 않습니다."),
    REPORT_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "이미 처리 완료된 신고입니다.");


    private final HttpStatus httpStatus;
    private final String message;

    ReportExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}