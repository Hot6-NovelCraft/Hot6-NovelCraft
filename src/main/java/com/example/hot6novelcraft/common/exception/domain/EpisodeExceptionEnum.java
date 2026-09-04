package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum EpisodeExceptionEnum implements ErrorCode {

    EPISODE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 회차를 찾을 수 없습니다."),
    EPISODE_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 회차만 수정/삭제할 수 있습니다."),
    EPISODE_ALREADY_DELETED(HttpStatus.NOT_FOUND, "이미 삭제된 회차입니다."),
    EPISODE_NUMBER_DUPLICATE(HttpStatus.BAD_REQUEST, "이미 존재하는 회차 번호입니다."),
    EPISODE_ALREADY_PUBLISHED(HttpStatus.BAD_REQUEST, "이미 발행된 회차입니다."),
    EPISODE_NUMBER_NOT_SEQUENTIAL(HttpStatus.BAD_REQUEST, "회차 번호는 순서대로 등록해야 합니다."),
    EPISODE_DELETE_NOT_LAST(HttpStatus.BAD_REQUEST, "마지막 회차만 삭제할 수 있습니다."),
    EPISODE_PREVIOUS_NOT_PUBLISHED(HttpStatus.BAD_REQUEST, "이전 회차를 먼저 발행해야 합니다."),
    EPISODE_NOT_PUBLISHED(HttpStatus.BAD_REQUEST, "발행되지 않은 회차입니다."),
    EPISODE_POINT_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "포인트 결제가 필요한 회차입니다."),
    EPISODE_CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "본문 내용이 없는 회차는 발행할 수 없습니다."),
    EPISODE_ALREADY_PURCHASED(HttpStatus.CONFLICT, "이미 구매한 회차입니다."),
    EPISODE_FREE_NO_PURCHASE(HttpStatus.BAD_REQUEST, "무료 회차는 구매할 수 없습니다."),
    EPISODE_NOT_AVAILABLE_FOR_PURCHASE(HttpStatus.BAD_REQUEST, "구매할 수 없는 회차입니다. (미발행 또는 삭제됨)"),
    NOVEL_NO_PURCHASABLE_EPISODES(HttpStatus.BAD_REQUEST, "구매 가능한 회차가 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다."),
    AI_REVIEW_LOCK_ACQUIRE_FAILED(HttpStatus.TOO_MANY_REQUESTS, "AI 리뷰 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."),

    // AI 리뷰 관련
    AI_REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 소설의 회차에만 AI 리뷰를 받을 수 있습니다."),
    AI_REVIEW_ONLY_DRAFT(HttpStatus.BAD_REQUEST, "발행 전(초안) 상태의 회차만 AI 리뷰를 받을 수 있습니다."),
    AI_REVIEW_CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "본문이 없어 AI 리뷰를 받을 수 없습니다."),
    AI_REVIEW_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 리뷰 작업을 찾을 수 없습니다. 만료되었거나 잘못된 조회 입니다."),
    AI_REVIEW_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 리뷰 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    EpisodeExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}