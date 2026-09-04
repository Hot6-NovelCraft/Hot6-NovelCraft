package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.domain.exchange.entity.Withdrawal;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long>, WithdrawalRepositoryCustom {

    boolean existsByAuthorIdAndStatus(Long authorId, WithdrawalStatus status);

    Optional<Withdrawal> findByIdAndAuthorId(Long id, Long authorId);
}