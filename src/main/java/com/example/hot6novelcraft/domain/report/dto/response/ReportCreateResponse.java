package com.example.hot6novelcraft.domain.report.dto.response;

public record ReportCreateResponse(Long reportId) {
    public static ReportCreateResponse from(Long reportId) {
        return new ReportCreateResponse(reportId);
    }
}
