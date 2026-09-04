package com.example.hot6novelcraft.domain.admin.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.admin.dto.request.AdminReportProcessRequest;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminReportListResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminReportProcessResponse;
import com.example.hot6novelcraft.domain.admin.service.AdminReportService;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportStatus;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Validated
public class AdminReportController {

    private final AdminReportService adminReportService;

    /**
     * 신고 목록 조회
     * 정은식
     */
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AdminReportListResponse>>> getReportList(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(defaultValue = "0") @Min(0) int page
    ) {
        Pageable pageable = PageRequest.of(page, 20);
        PageResponse<AdminReportListResponse> response =
                adminReportService.getReportList(status, targetType, pageable);

        return ResponseEntity.ok(
                BaseResponse.success("200", "신고 목록 조회 성공", response)
        );
    }

    /**
     * 신고 처리
     * 정은식
     */
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/{reportId}")
    public ResponseEntity<BaseResponse<AdminReportProcessResponse>> processReport(
            @PathVariable Long reportId,
            @Valid @RequestBody AdminReportProcessRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        AdminReportProcessResponse response =
                adminReportService.processReport(reportId, request, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "신고 처리가 완료되었습니다.", response)
        );
    }
}
