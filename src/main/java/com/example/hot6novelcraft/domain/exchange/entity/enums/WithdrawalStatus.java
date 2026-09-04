package com.example.hot6novelcraft.domain.exchange.entity.enums;

import java.util.Map;
import java.util.Set;

public enum WithdrawalStatus {
    PENDING,        // 승인 대기
    PROCESSING,     // 처리 중
    COMPLETED,      // 완료
    REJECTED;       // 거절

    private static final Map<WithdrawalStatus, Set<WithdrawalStatus>> VALID_TRANSITIONS = Map.of(
            PENDING, Set.of(PROCESSING, REJECTED),
            PROCESSING, Set.of(COMPLETED, REJECTED),
            COMPLETED, Set.of(),
            REJECTED, Set.of()
    );

    public boolean canTransitionTo(WithdrawalStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}