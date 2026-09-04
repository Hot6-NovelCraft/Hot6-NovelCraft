package com.example.hot6novelcraft.domain.exchange.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bank_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String bankName;

    @Column(nullable = false)
    private String accountNumber; // AES 암호화된 계좌번호

    @Column(nullable = false, length = 50)
    private String accountHolder;

    @Column(nullable = false)
    private Boolean isVerified;

    private BankAccount(Long userId, String bankName, String accountNumber, String accountHolder) {
        this.userId = userId;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.isVerified = false;
    }

    public static BankAccount create(Long userId, String bankName, String encryptedAccountNumber, String accountHolder) {
        return new BankAccount(userId, bankName, encryptedAccountNumber, accountHolder);
    }

    public void verify() {
        this.isVerified = true;
    }

    /*
     * 마스킹된 계좌번호 반환 (뒤 4자리만 노출)
     * ex) ************1234
     */
    public String getMaskedAccountNumber(String decryptedNumber) {
        if (decryptedNumber == null || decryptedNumber.length() <= 4) {
            return "****";
        }
        String lastFour = decryptedNumber.substring(decryptedNumber.length() - 4);
        String masked = "*".repeat(decryptedNumber.length() - 4);
        return masked + lastFour;
    }
}