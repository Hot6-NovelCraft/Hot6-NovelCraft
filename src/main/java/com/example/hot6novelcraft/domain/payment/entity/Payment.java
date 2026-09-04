package com.example.hot6novelcraft.domain.payment.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentMethod;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    // 포트원 V2 paymentId (서버에서 생성하여 프론트에 전달하는 주문 ID)
    @Column(nullable = false, unique = true)
    private String paymentKey;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(value = EnumType.STRING)
    private PaymentMethod method;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime cancelledAt;

    private Payment(Long userId, String paymentKey, Long amount, PaymentMethod method) {
        this.userId = userId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long userId, String paymentKey, Long amount, PaymentMethod method) {
        return new Payment(userId, paymentKey, amount, method);
    }

    public void complete(PaymentMethod method) {
        this.status = PaymentStatus.COMPLETED;
        this.method = method;
    }

    public void cancel() {
        this.status = PaymentStatus.REFUNDED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }
}
