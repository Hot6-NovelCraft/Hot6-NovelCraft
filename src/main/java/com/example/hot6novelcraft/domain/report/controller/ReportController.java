package com.example.hot6novelcraft.domain.report.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.report.dto.request.ReportCreateRequest;
import com.example.hot6novelcraft.domain.report.dto.response.ReportCreateResponse;
import com.example.hot6novelcraft.domain.report.service.ReportService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    /**
     * 신고 등록 (회차 댓글 신고 / 유저 신고-멘토멘티)
     * 정은식
     */
    @PostMapping
    public ResponseEntity<BaseResponse<ReportCreateResponse>> createReport(
            @Valid @RequestBody ReportCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        ReportCreateResponse response = reportService.createReport(request, userDetails);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "신고가 접수되었습니다.", response));
    }
}