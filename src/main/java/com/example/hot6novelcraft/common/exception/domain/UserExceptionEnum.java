package com.example.hot6novelcraft.common.exception.domain;

import com.example.hot6novelcraft.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum UserExceptionEnum implements ErrorCode {
    ERR_NOT_FOUND_USER(HttpStatus.NOT_FOUND, "존재하지 않거나 탈퇴한 사용자입니다")
    , ERR_INVALID_ROLE(HttpStatus.BAD_REQUEST, "역할을 찾을 수 없습니다")
    , ERR_NOT_FOUND_EMAIL(HttpStatus.NOT_FOUND, "이메일을 찾을 수 없습니다")

    // 관리자
    , ERR_NOT_PENDING_ADMIN(HttpStatus.BAD_REQUEST, "승인 대기 중인 관리자 계정이 아닙니다")
    , ERR_NOT_POSSIBLE_TO_REFUSE(HttpStatus.BAD_REQUEST, "승인 거절 가능한 상태가 아닙니다")

    // 회원가입
    , ERR_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다")
    , ERR_INVALID_EMAIL(HttpStatus.BAD_REQUEST, "이메일 형식이 올바르지 않습니다")
    , ERR_NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다")
    , ERR_INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임은 2~10자이내로 입력하세요")
    , ERR_INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다")
    , ERR_ALREADY_COMPLETED_SIGNUP(HttpStatus.CONFLICT, "이미 회원가입이 되어있습니다")
    , ERR_ADDITIONAL_INFORMATION(HttpStatus.BAD_REQUEST, "추가 정보 입력이 필요합니다")

    // 휴대폰 인증
    , ERR_INVALID_PHONE_NO(HttpStatus.BAD_REQUEST, "휴대폰 번호형식이 올바르지 않습니다")
    , ERR_PHONE_NO_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 휴대폰 번호입니다")
    , ERR_FAILED_SEND_SMS(HttpStatus.INTERNAL_SERVER_ERROR, "SMS 전송이 실패했습니다")
    , ERR_INVALID_PHONE(HttpStatus.BAD_REQUEST, "인증번호가 만료되었습니다")
    , ERR_INVALID_PHONE_VERIFICATION(HttpStatus.UNAUTHORIZED,"휴대폰 인증이 유효하지 않습니다")
    , ERR_PHONE_NOT_VERIFIED(HttpStatus.CONFLICT, "휴대폰 인증을 다시해주세요")
    , ERR_EXCEED_SMS_LIMIT(HttpStatus.BAD_REQUEST, "휴대폰 일일 전송 횟수(5회)가 초과되었습니다. 24시간 후 다시 시도해주세요")

    // 로그인
    , ERR_FAILED_SOCIAL(HttpStatus.NOT_FOUND, "소셜 인증에 실패했습니다")
    , ERR_INVALID_EMAIL_OR_PASSWORD(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다")

    // 회원탈퇴
    , ERR_USER_WITHDRAWAL_PENDING_CONFLICT(HttpStatus.CONFLICT, "탈퇴 진행 중인 계정입니다. 복구 후 이용해주세요")
    , ERR_USER_WITHDRAWAL_PENDING_FORBIDDEN(HttpStatus.FORBIDDEN, "탈퇴 진행 중인 계정입니다. 복구 하시겠습니까?")
    , ERR_NOT_WITHDRAWAL_PENDING(HttpStatus.NOT_FOUND, "탈퇴 유예 상태인 유저만 가능한 요청입니다")

    // 회원정보
    , ERR_NOT_FOUND_AUTHOR_PROFILE(HttpStatus.NOT_FOUND,"작가 프로필을 찾을 수 없습니다")
    , ERR_SOCIAL_USER_CANNOT_CHANGE_PASSWORD(HttpStatus.BAD_REQUEST, "소셜 로그인 유저는 비밀번호를 변경할 수 없습니다")
    , ERR_PASSWORD_NOT_MATCH(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 일치 하지 않습니다")
    , ERR_SAME_AS_OLD_PASSWORD(HttpStatus.CONFLICT, "현재 비밀번호와 동일합니다. 다시 입력해주세요")
    , ERR_ADULT_VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "성인 인증이 필요한 컨텐츠입니다")

    // 작가 팔로우
    , ERR_SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인을 팔로우할 수 없습니다")
    , ERR_NOT_AUTHOR(HttpStatus.BAD_REQUEST, "작가가 아닌 유저는 팔로우할 수 없습니다")

    // Token
    , ERR_ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AccessToken이 만료되었습니다. 재발급해주세요")
    , ERR_REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "RefreshToke이 만료되었습니다. 다시 로그인해주세요")
    , ERR_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다")
    , REDIS_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "인증 캐시 서버 연결에 실패했습니다. 다시 시도해주세요");

    private final HttpStatus httpStatus;
    private final String message;

    UserExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
