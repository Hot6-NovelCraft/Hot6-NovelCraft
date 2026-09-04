package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.domain.exchange.entity.Withdrawal;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface WithdrawalRepositoryCustom {

    Page<Withdrawal> findWithFilters(
            Long authorId,
            WithdrawalStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    // 관리자 전체 환전 내역 조회 (authorId 없음)
    Page<Withdrawal> findAllWithFilters(
            WithdrawalStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );
}