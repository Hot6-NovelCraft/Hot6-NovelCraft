package com.example.hot6novelcraft.domain.subscription.entity.enums;

public enum SubscriptionStatus {

    PENDING,    // 구독 준비 중 (빌링키 발급 대기)
    ACTIVE,     // 활성 구독
    CANCELLED   // 취소됨
}
