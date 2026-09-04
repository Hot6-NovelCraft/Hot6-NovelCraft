package com.example.hot6novelcraft.domain.admin.dto.response;

import com.example.hot6novelcraft.domain.admin.entity.ReportManagement;
import com.example.hot6novelcraft.domain.admin.entity.enums.ResultStatus;

import java.time.LocalDateTime;

public record AdminReportProcessResponse(
        Long reportManagementId,
        Long reportId,
        Long targetId,
        ResultStatus resultStatus,
        String adminMemo,
        LocalDateTime createdAt
) {
    public static AdminReportProcessResponse from(ReportManagement reportManagement) {
        return new AdminReportProcessResponse(
                reportManagement.getId(),
                reportManagement.getReportId(),
                reportManagement.getTargetId(),
                reportManagement.getResultStatus(),
                reportManagement.getAdminMemo(),
                reportManagement.getCreatedAt()
        );
    }
}