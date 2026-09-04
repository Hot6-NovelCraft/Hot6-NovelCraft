package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.domain.exchange.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByUserIdAndIsVerifiedTrue(Long userId);

    Optional<BankAccount> findByUserId(Long userId);

    List<BankAccount> findAllByUserId(Long userId);

    boolean existsByAccountNumber(String encryptedAccountNumber);

    boolean existsByUserIdAndIsVerifiedTrue(Long userId);

    boolean existsByAccountNumberAndIsVerifiedTrue(String encryptedAccountNumber);

    void deleteAllByUserId(Long userId);
}