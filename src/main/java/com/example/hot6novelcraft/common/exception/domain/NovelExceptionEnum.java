package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum NovelExceptionEnum implements ErrorCode {

    NOVEL_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 소설을 찾을 수 없습니다."),
    NOVEL_AUTHOR_FORBIDDEN(HttpStatus.FORBIDDEN, "작가 권한이 필요합니다."),
    NOVEL_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 소설만 수정/삭제할 수 있습니다."),
    NOVEL_ALREADY_DELETED(HttpStatus.NOT_FOUND, "이미 삭제된 소설입니다."),

    // 찜
    NOVEL_SELF_BOOKMARK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인 소설은 찜할 수 없습니다."),
    NOVEL_NOT_VIEWABLE(HttpStatus.BAD_REQUEST, "볼 수 없는 상태의 소설입니다."),

    NOVEL_STAT_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 소설만 통계를 조회할 수 있습니다."),

    // 랭킹
    INVALID_RANKING_TYPE(HttpStatus.BAD_REQUEST, "유효하지 않은 랭킹 타입입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    NovelExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}