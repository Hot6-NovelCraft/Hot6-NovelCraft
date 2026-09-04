package com.example.hot6novelcraft.domain.exchange.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountVerification extends BaseEntity {

    private static final int MAX_ATTEMPT_COUNT = 5;
    private static final int EXPIRATION_MINUTES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bankAccountId;

    @Column(nullable = false, length = 10)
    private String verificationCode;

    @Column(nullable = false)
    private Boolean isVerified;

    @Column(nullable = false)
    private Integer attemptCount;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    private AccountVerification(Long bankAccountId, String verificationCode) {
        this.bankAccountId = bankAccountId;
        this.verificationCode = verificationCode;
        this.isVerified = false;
        this.attemptCount = 0;
        this.expiredAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);
    }

    public static AccountVerification create(Long bankAccountId, String verificationCode) {
        return new AccountVerification(bankAccountId, verificationCode);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiredAt);
    }

    public boolean isMaxAttemptExceeded() {
        return this.attemptCount >= MAX_ATTEMPT_COUNT;
    }

    public void increaseAttemptCount() {
        this.attemptCount++;
    }

    public void verify() {
        this.isVerified = true;
    }
}