package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.domain.exchange.entity.AccountVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountVerificationRepository extends JpaRepository<AccountVerification, Long> {

    Optional<AccountVerification> findTopByBankAccountIdAndIsVerifiedFalseOrderByCreatedAtDesc(Long bankAccountId);
}