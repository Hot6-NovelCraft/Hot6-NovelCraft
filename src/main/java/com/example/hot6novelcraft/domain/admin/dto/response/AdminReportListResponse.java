package com.example.hot6novelcraft.domain.admin.dto.response;

import com.example.hot6novelcraft.domain.report.entity.enums.ReportStatus;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;

import java.time.LocalDateTime;

public record AdminReportListResponse(
        Long reportId,
        Long reporterId,
        String reporterNickname,
        Long targetId,
        String targetNickname,
        Long commentId,
        String reason,
        ReportStatus status,
        ReportTargetType targetType,
        LocalDateTime createdAt
) {
}