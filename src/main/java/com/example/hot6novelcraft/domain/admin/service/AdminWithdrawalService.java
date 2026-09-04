package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalDetailResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalResponse;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import com.example.hot6novelcraft.domain.exchange.service.WithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j(topic = "ADMIN")
@Service
@RequiredArgsConstructor
@Transactional
public class AdminWithdrawalService {

    private final WithdrawalService withdrawalService;

    /**
     * 전체 환전 신청 목록 조회
     */
    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> getAllWithdrawals(
            WithdrawalStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        return withdrawalService.getAllWithdrawals(status, startDate, endDate, pageable);
    }

    /**
     * 환전 상세 조회
     */
    @Transactional(readOnly = true)
    public WithdrawalDetailResponse getWithdrawalDetail(Long withdrawalId) {
        return withdrawalService.getWithdrawalDetailForAdmin(withdrawalId);
    }

    /**
     * 환전 승인 - PENDING → PROCESSING → COMPLETED
     */
    public WithdrawalResponse approveWithdrawal(Long withdrawalId) {
        log.info("[관리자] 환전 승인 요청 - withdrawalId: {}", withdrawalId);
        return withdrawalService.approveWithdrawal(withdrawalId);
    }

    /**
     * 환전 거절 - PENDING → REJECTED + 잔액 복구
     */
    public WithdrawalResponse rejectWithdrawal(Long withdrawalId, String rejectedReason) {
        log.info("[관리자] 환전 거절 요청 - withdrawalId: {}", withdrawalId);
        return withdrawalService.rejectWithdrawal(withdrawalId, rejectedReason);
    }
}