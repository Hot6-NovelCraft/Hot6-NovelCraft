package com.example.hot6novelcraft.domain.webhookevent.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "webhook_events")
public class WebhookEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 포트원 V2 transactionId — 멱등성 처리 키
    @Column(nullable = false, unique = true)
    private String webhookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookEventStatus status;

    // 포트원 V2 이벤트 타입 (Transaction.Paid 등)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookEventType eventType;

    // 포트원 V2 paymentId
    @Column(nullable = false)
    private String paymentId;

    // 원본 웹훅 JSON — 재처리 및 디버깅용
    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    // FAIL 상태일 때 실패 사유
    private String errorMessage;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime completeAt;

    private WebhookEvent(String webhookId, WebhookEventType eventType, String paymentId, String rawPayload) {
        this.webhookId = webhookId;
        this.status = WebhookEventStatus.PENDING;
        this.eventType = eventType;
        this.paymentId = paymentId;
        this.rawPayload = rawPayload;
        this.retryCount = 0;
    }

    public static WebhookEvent create(String webhookId, WebhookEventType eventType, String paymentId, String rawPayload) {
        return new WebhookEvent(webhookId, eventType, paymentId, rawPayload);
    }

    public void complete() {
        this.status = WebhookEventStatus.COMPLETE;
        this.completeAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = WebhookEventStatus.FAIL;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }
}
