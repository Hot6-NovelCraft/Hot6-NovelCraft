package com.example.hot6novelcraft.domain.admin.repository;

import com.example.hot6novelcraft.domain.admin.dto.response.AdminReportListResponse;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportStatus;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomAdminReportRepository {

    Page<AdminReportListResponse> findReportList(
            ReportStatus status,
            ReportTargetType targetType,
            Pageable pageable
    );
}