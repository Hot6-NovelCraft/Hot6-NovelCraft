package com.example.hot6novelcraft.domain.exchange.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.exchange.dto.request.WithdrawalCreateRequest;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalDetailResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalResponse;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import com.example.hot6novelcraft.domain.exchange.service.WithdrawalService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/revenues/me/exchanges")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    /**
     * 환전 신청
     */
    @PostMapping
    public ResponseEntity<BaseResponse<WithdrawalResponse>> createWithdrawal(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody WithdrawalCreateRequest request
    ) {
        Long userId = userDetails.getUser().getId();
        WithdrawalResponse response = withdrawalService.createWithdrawal(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("CREATED", "환전 신청이 완료되었습니다", response));
    }

    /**
     * 환전 내역 목록 조회
     */
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<WithdrawalResponse>>> getWithdrawals(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) WithdrawalStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Long userId = userDetails.getUser().getId();
        PageResponse<WithdrawalResponse> response = withdrawalService.getWithdrawals(
                userId, status, startDate, endDate, pageable
        );
        return ResponseEntity.ok(BaseResponse.success("OK", "환전 내역을 조회했습니다", response));
    }

    /**
     * 환전 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<WithdrawalDetailResponse>> getWithdrawalDetail(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id
    ) {
        Long userId = userDetails.getUser().getId();
        WithdrawalDetailResponse response = withdrawalService.getWithdrawalDetail(userId, id);
        return ResponseEntity.ok(BaseResponse.success("OK", "환전 상세 내역을 조회했습니다", response));
    }
}