package com.example.hot6novelcraft.domain.webhookevent.entity;

/**
 * 포트원 V2 웹훅 이벤트 타입
 * Transaction.Paid       -> TRANSACTION_PAID
 * Transaction.Failed     -> TRANSACTION_FAILED
 * Transaction.Cancelled  -> TRANSACTION_CANCELLED
 */
public enum WebhookEventType {

    TRANSACTION_PAID,
    TRANSACTION_FAILED,
    TRANSACTION_CANCELLED
}
