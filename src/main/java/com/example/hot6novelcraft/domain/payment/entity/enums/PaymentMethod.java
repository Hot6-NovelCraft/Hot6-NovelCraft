package com.example.hot6novelcraft.domain.payment.entity.enums;

import io.portone.sdk.server.common.EasyPayProvider;
import io.portone.sdk.server.payment.PaymentMethodEasyPay;
import io.portone.sdk.server.payment.PaymentMethodMobile;
import io.portone.sdk.server.payment.PaymentMethodTransfer;
import io.portone.sdk.server.payment.PaymentMethodVirtualAccount;

public enum PaymentMethod {

    /** 신용/체크카드 */
    CARD,

    /** 간편결제 */
    KAKAOPAY,
    TOSSPAY,
    NAVERPAY,
    SAMSUNGPAY,
    PAYCO,
    /** 위에 매핑된 간편결제 외 기타 간편결제 */
    EASY_PAY,

    /** 휴대폰 소액결제 */
    MOBILE,

    /** 가상계좌 */
    VIRTUAL_ACCOUNT,

    /** 계좌이체 */
    TRANSFER;

    /**
     * 포트원 V2 SDK PaymentMethod → 내부 PaymentMethod 변환.
     * 매핑되지 않는 타입(카드·null 포함)은 CARD를 반환한다.
     */
    public static PaymentMethod from(io.portone.sdk.server.payment.PaymentMethod portOneMethod) {
        if (portOneMethod instanceof PaymentMethodEasyPay easyPay) {
            return fromProvider(easyPay.getProvider());
        }
        if (portOneMethod instanceof PaymentMethodMobile) return MOBILE;
        if (portOneMethod instanceof PaymentMethodVirtualAccount) return VIRTUAL_ACCOUNT;
        if (portOneMethod instanceof PaymentMethodTransfer) return TRANSFER;
        return CARD;
    }

    private static PaymentMethod fromProvider(EasyPayProvider provider) {
        if (provider instanceof EasyPayProvider.Kakaopay) return KAKAOPAY;
        if (provider instanceof EasyPayProvider.Tosspay) return TOSSPAY;
        if (provider instanceof EasyPayProvider.Naverpay) return NAVERPAY;
        if (provider instanceof EasyPayProvider.Samsungpay) return SAMSUNGPAY;
        if (provider instanceof EasyPayProvider.Payco) return PAYCO;
        return EASY_PAY;
    }
}
