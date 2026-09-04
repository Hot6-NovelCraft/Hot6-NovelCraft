package com.example.hot6novelcraft.domain.exchange.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueOverviewResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueStatisticsResponse;
import com.example.hot6novelcraft.domain.exchange.entity.enums.StatisticsPeriod;
import com.example.hot6novelcraft.domain.exchange.service.RevenueService;
import com.example.hot6novelcraft.domain.exchange.service.StatisticsService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/revenues/me")
public class RevenueController {

    private final RevenueService revenueService;
    private final StatisticsService statisticsService;

    /**
     * 수익 현황 조회
     */
    @GetMapping
    public ResponseEntity<BaseResponse<RevenueOverviewResponse>> getRevenueOverview(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Long userId = userDetails.getUser().getId();
        RevenueOverviewResponse response = revenueService.getRevenueOverview(userId);
        return ResponseEntity.ok(BaseResponse.success("OK", "수익 현황을 조회했습니다", response));
    }

    /**
     * 수익 분석 통계 조회
     */
    @GetMapping("/statistics")
    public ResponseEntity<BaseResponse<RevenueStatisticsResponse>> getStatistics(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "MONTHLY") StatisticsPeriod period,
            @RequestParam(required = false) Integer year
    ) {
        Long userId = userDetails.getUser().getId();
        int resolvedYear = (year != null) ? year : java.time.Year.now().getValue();
        RevenueStatisticsResponse response = statisticsService.getStatistics(userId, period, resolvedYear);
        return ResponseEntity.ok(BaseResponse.success("OK", "수익 통계를 조회했습니다", response));
    }
}