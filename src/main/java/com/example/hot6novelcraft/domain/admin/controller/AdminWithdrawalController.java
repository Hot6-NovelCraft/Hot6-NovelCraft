package com.example.hot6novelcraft.domain.admin.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.admin.service.AdminWithdrawalService;
import com.example.hot6novelcraft.domain.exchange.dto.request.WithdrawalRejectRequest;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalDetailResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalResponse;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/exchanges")
@Secured({"ADMIN", "SUPER_ADMIN"})
public class AdminWithdrawalController {

    private final AdminWithdrawalService adminWithdrawalService;

    /**
     * 전체 환전 신청 목록 조회
     */
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<WithdrawalResponse>>> getAllWithdrawals(
            @RequestParam(required = false) WithdrawalStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // [CodeRabbit] startDate > endDate 역전 검증
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일이 종료일보다 클 수 없습니다");
        }

        PageResponse<WithdrawalResponse> response =
                adminWithdrawalService.getAllWithdrawals(status, startDate, endDate, pageable);
        return ResponseEntity.ok(BaseResponse.success("OK", "환전 신청 목록을 조회했습니다", response));
    }

    /**
     * 환전 상세 조회
     */
    @GetMapping("/{withdrawalId}")
    public ResponseEntity<BaseResponse<WithdrawalDetailResponse>> getWithdrawalDetail(
            @PathVariable Long withdrawalId
    ) {
        WithdrawalDetailResponse response = adminWithdrawalService.getWithdrawalDetail(withdrawalId);
        return ResponseEntity.ok(BaseResponse.success("OK", "환전 상세 내역을 조회했습니다", response));
    }

    /**
     * 환전 승인
     */
    @PutMapping("/{withdrawalId}/approve")
    public ResponseEntity<BaseResponse<WithdrawalResponse>> approve(
            @PathVariable Long withdrawalId
    ) {
        WithdrawalResponse response = adminWithdrawalService.approveWithdrawal(withdrawalId);
        return ResponseEntity.ok(BaseResponse.success("OK", "환전이 승인되었습니다", response));
    }

    /**
     * 환전 거절
     */
    @PutMapping("/{withdrawalId}/reject")
    public ResponseEntity<BaseResponse<WithdrawalResponse>> reject(
            @PathVariable Long withdrawalId,
            @Valid @RequestBody WithdrawalRejectRequest request
    ) {
        WithdrawalResponse response = adminWithdrawalService.rejectWithdrawal(withdrawalId, request.rejectedReason());
        return ResponseEntity.ok(BaseResponse.success("OK", "환전이 거절되었습니다", response));
    }
}