package com.example.hot6novelcraft.domain.user.entity.enums;

import lombok.Getter;

@Getter
public enum UserRole {
    READER,         // 독자
    AUTHOR,         // 작가
    TEMP,           // 사용자 공통 가입 완료 후 임시 상태
    SUPER_ADMIN,    // 최고 관리자
    PENDING_ADMIN,  // 일반 관리자 승인 대기 상태 (superAdmin 승인 필요)
    REJECTED_ADMIN, // 승인 거절 일반 관리자
    ADMIN           // 일반 관리자
}
