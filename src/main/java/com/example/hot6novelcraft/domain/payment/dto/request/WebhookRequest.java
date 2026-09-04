package com.example.hot6novelcraft.domain.payment.dto.request;

/**
 * 포트원 V2 웹훅 페이로드
 *
 * V2 웹훅 형식:
 * {
 * "type": "Transaction.Paid",
 * "data": {
 * "paymentId": "payment-xxx",
 * "transactionId": "xxx"
 * }
 * }
 *
 * type 종류: Transaction.Paid | Transaction.Failed | Transaction.Cancelled
 */
public record WebhookRequest(
        String type,
        WebhookData data
) {
    public record WebhookData(
            String paymentId,
            String transactionId,
            String cancellationId
    ) {
    }
}
