package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.ReportExceptionEnum;
import com.example.hot6novelcraft.domain.admin.dto.request.AdminReportProcessRequest;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminReportListResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminReportProcessResponse;
import com.example.hot6novelcraft.domain.admin.entity.ReportManagement;
import com.example.hot6novelcraft.domain.admin.repository.AdminReportRepository;
import com.example.hot6novelcraft.domain.admin.repository.ReportManagementRepository;
import com.example.hot6novelcraft.domain.report.entity.Report;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportStatus;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j(topic = "AdminReportService")
@Service
@RequiredArgsConstructor
@Transactional
public class AdminReportService {

    private final AdminReportRepository adminReportRepository;
    private final ReportManagementRepository reportManagementRepository;

    // 신고 목록 조회 (필터링 + 페이징)
    @Transactional(readOnly = true)
    public PageResponse<AdminReportListResponse> getReportList(ReportStatus status, ReportTargetType targetType, Pageable pageable
    ) {
        Page<AdminReportListResponse> reports =
                adminReportRepository.findReportList(status, targetType, pageable);

        return PageResponse.register(reports);
    }

    // 신고 처리
    @Transactional
    public AdminReportProcessResponse processReport(
            Long reportId,
            AdminReportProcessRequest request,
            UserDetailsImpl userDetails
    ) {
        Long adminId = userDetails.getUser().getId();

        // 신고 조회
        Report report = adminReportRepository.findById(reportId)
                .orElseThrow(() -> new ServiceErrorException(ReportExceptionEnum.REPORT_NOT_FOUND));

        // 이미 완료된 신고 재처리 방지
        if (report.getStatus() == ReportStatus.COMPLETION) {
            throw new ServiceErrorException(ReportExceptionEnum.REPORT_ALREADY_COMPLETED);
        }

        // 중복 처리 방지 (이미 report_management 기록 있으면 차단)
        if (reportManagementRepository.existsByReportId(reportId)) {
            throw new ServiceErrorException(ReportExceptionEnum.REPORT_ALREADY_COMPLETED);
        }

        // 신고 상태 COMPLETION으로 변경
        report.changeStatus(ReportStatus.COMPLETION);

        // 신고 처리 기록 저장
        ReportManagement reportManagement = ReportManagement.builder()
                .reportId(reportId)
                .targetId(report.getTargetId())
                .adminId(adminId)
                .adminMemo(request.adminMemo())
                .resultStatus(request.resultStatus())
                .build();

        ReportManagement savedManagement = reportManagementRepository.save(reportManagement);

        // 누적 처리 횟수 로그
        long count = reportManagementRepository.countByTargetId(report.getTargetId());
        log.info("[신고 처리] reportId={}, targetId={}, resultStatus={}, 누적처리횟수={}",
                reportId, report.getTargetId(), request.resultStatus(), count);

        return AdminReportProcessResponse.from(savedManagement);
    }
}
