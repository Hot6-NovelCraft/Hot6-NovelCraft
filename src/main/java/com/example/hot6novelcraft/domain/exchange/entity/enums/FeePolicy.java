package com.example.hot6novelcraft.domain.exchange.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeePolicy {

    DEFAULT(0.10, 10_000);  // 10% 수수료, 최소 환전 금액 10,000원

    private final double feeRate;
    private final int minimumWithdrawalAmount;

    public int calculateFee(int requestAmount) {
        return (int) Math.floor(requestAmount * feeRate);
    }

    public int calculateActualAmount(int requestAmount) {
        return requestAmount - calculateFee(requestAmount);
    }
}