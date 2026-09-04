package com.example.hot6novelcraft.domain.exchange.dto.response;

import java.util.List;

public record RevenueStatisticsResponse(
        String period,                          // WEEKLY or MONTHLY
        Integer year,
        Integer totalAmount,                    // 해당 기간 총 수익
        List<RevenueStatisticsItem> items       // 기간별 항목 리스트
) {
    public static RevenueStatisticsResponse of(
            String period, Integer year, Integer totalAmount, List<RevenueStatisticsItem> items
    ) {
        return new RevenueStatisticsResponse(period, year, totalAmount, items);
    }

    public record RevenueStatisticsItem(
            String label,               // "2026-01" (월별) or "2026-W01" (주별)
            Integer episodeSaleAmount,   // 회차 판매 수익
            Integer subscriptionAmount,  // 구독 수익
            Integer totalAmount          // 소계
    ) {
        public static RevenueStatisticsItem of(
                String label, Integer episodeSaleAmount, Integer subscriptionAmount
        ) {
            return new RevenueStatisticsItem(
                    label, episodeSaleAmount, subscriptionAmount,
                    episodeSaleAmount + subscriptionAmount
            );
        }
    }
}