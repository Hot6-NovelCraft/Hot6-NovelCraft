package com.example.hot6novelcraft.domain.subscription.entity.enums;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.SubscriptionExceptionEnum;
import lombok.Getter;

@Getter
public enum PlanType {

    PREMIUM(9_900L);  // 월 9,900원

    private final Long price;

    PlanType(Long price) {
        this.price = price;
    }

    /**
     * 요청 금액이 플랜 정가와 일치하는지 검증
     */
    public void validateAmount(Long requestAmount) {
        if (!this.price.equals(requestAmount)) {
            throw new ServiceErrorException(SubscriptionExceptionEnum.ERR_AMOUNT_MISMATCH);
        }
    }
}
