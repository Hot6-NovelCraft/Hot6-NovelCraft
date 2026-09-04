package com.example.hot6novelcraft.domain.exchange.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "withdrawals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Withdrawal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private Long bankAccountId;

    @Column(nullable = false)
    private Integer requestAmount;

    @Column(nullable = false)
    private Integer fee;

    @Column(nullable = false)
    private Integer actualAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalStatus status;

    @Column(length = 255)
    private String rejectedReason;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    private Withdrawal(Long authorId, Long bankAccountId, Integer requestAmount, Integer fee) {
        this.authorId = authorId;
        this.bankAccountId = bankAccountId;
        this.requestAmount = requestAmount;
        this.fee = fee;
        this.actualAmount = requestAmount - fee;
        this.status = WithdrawalStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    public static Withdrawal request(Long authorId, Long bankAccountId, Integer requestAmount, Integer fee) {
        return new Withdrawal(authorId, bankAccountId, requestAmount, fee);
    }

    public void processing() {
        validateTransition(WithdrawalStatus.PROCESSING);
        this.status = WithdrawalStatus.PROCESSING;
    }

    public void complete() {
        validateTransition(WithdrawalStatus.COMPLETED);
        this.status = WithdrawalStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void reject(String reason) {
        validateTransition(WithdrawalStatus.REJECTED);
        this.status = WithdrawalStatus.REJECTED;
        this.rejectedReason = reason;
        this.processedAt = LocalDateTime.now();
    }

    private void validateTransition(WithdrawalStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("환전 상태를 %s에서 %s로 변경할 수 없습니다", this.status, target)
            );
        }
    }
}