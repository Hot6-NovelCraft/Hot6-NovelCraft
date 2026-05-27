package com.example.hot6novelcraft.domain.admin.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardResponse;
import com.example.hot6novelcraft.domain.admin.service.AdminDashboardService;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * ======= v1 쿼리 분할 =======
     */
    @GetMapping("/v1")
    public ResponseEntity<BaseResponse<AdminDashboardResponse>> getDashboardSeparated(
            @RequestParam(required = false) UserRole role
            , @RequestParam(required = false, defaultValue = "ALL") String novelTotalStatus
            , @RequestParam(required = false) NovelStatus novelStatus
            , @RequestParam(required = false) Boolean isDeleted
    ) {
        AdminDashboardResponse response = adminDashboardService.getDashboardStatusSeparated(role, novelTotalStatus, novelStatus, isDeleted);
        return ResponseEntity.ok(BaseResponse.success("200", "V1 분할 - 통계 출력 완료", response));
    }

    /**
     * ======= v2 쿼리 병합 + indexing =======
     */
    @GetMapping("/v2")
    public ResponseEntity<BaseResponse<AdminDashboardResponse>> getDashboardIntegrated(
            @RequestParam(required = false) UserRole role
            , @RequestParam(required = false, defaultValue = "ALL") String novelTotalStatus
            , @RequestParam(required = false) NovelStatus novelStatus
            , @RequestParam(required = false) Boolean isDeleted
    ) {
        AdminDashboardResponse response = adminDashboardService.getDashboardStatusIntegrated(role, novelTotalStatus, novelStatus, isDeleted);
        return ResponseEntity.ok(BaseResponse.success("200", "V2 병합 - 통계 출력 완료", response));
    }

    /**
     * ======= v3 신규 실시간 (Redis + 병합 쿼리) =======
     */
    @GetMapping("/live")
    public ResponseEntity<BaseResponse<AdminDashboardResponse>> getLiveDashboard(
            @RequestParam(required = false) UserRole role
            , @RequestParam(required = false, defaultValue = "ALL") String novelTotalStatus
            , @RequestParam(required = false) NovelStatus novelStatus
            , @RequestParam(required = false) Boolean isDeleted
    ) {
        AdminDashboardResponse response = adminDashboardService.getLiveDashboard(role, novelTotalStatus, novelStatus, isDeleted);
        return ResponseEntity.ok(BaseResponse.success("200", "실시간 통계 출력 완료", response));
    }

    /**
     * ======= 과거 신규 통계 조회 (스냅샷) =======
     */
    @GetMapping("/history")
    public ResponseEntity<BaseResponse<AdminDashboardResponse>> getHistoryDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate
    ) {
        AdminDashboardResponse response = adminDashboardService.getHistoryDashBoard(targetDate);
        return ResponseEntity.ok(BaseResponse.success("200", "과거 신규 통계 출력 완료", response));
    }
}