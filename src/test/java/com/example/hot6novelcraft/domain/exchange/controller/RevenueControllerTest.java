package com.example.hot6novelcraft.domain.exchange.controller;

import com.example.hot6novelcraft.domain.exchange.dto.response.BankAccountInfoResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueOverviewResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueStatisticsResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueStatisticsResponse.RevenueStatisticsItem;
import com.example.hot6novelcraft.domain.exchange.entity.enums.StatisticsPeriod;
import com.example.hot6novelcraft.domain.exchange.service.RevenueService;
import com.example.hot6novelcraft.domain.exchange.service.StatisticsService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.service.UserCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Year;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RevenueController.class)
@AutoConfigureMockMvc(addFilters = false)
class RevenueControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean RevenueService revenueService;
    @MockBean StatisticsService statisticsService;
    @MockBean com.example.hot6novelcraft.common.security.JwtUtil jwtUtil;
    @MockBean com.example.hot6novelcraft.common.security.RedisUtil redisUtil;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserCacheService userCacheService;
    @MockBean JpaMetamodelMappingContext jpaMappingContext;

    @BeforeEach
    void setUp() {
        User mockUser = mock(User.class);
        given(mockUser.getId()).willReturn(1L);
        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(mockUser);
        given(userDetails.getAuthorities()).willReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of())
        );
    }

    @Nested
    @DisplayName("GET /api/revenues/me")
    class GetOverview {

        @Test
        @DisplayName("성공 - 수익 현황 + 계좌 정보")
        void successWithAccount() throws Exception {
            BankAccountInfoResponse bankInfo = BankAccountInfoResponse.of(
                    1L, "국민은행", "******7890", "홍길동", true);
            RevenueOverviewResponse response = RevenueOverviewResponse.of(500000, 100000, 400000,0,0, bankInfo);
            given(revenueService.getRevenueOverview(1L)).willReturn(response);

            mockMvc.perform(get("/api/revenues/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalEarned").value(500000))
                    .andExpect(jsonPath("$.data.totalWithdrawn").value(100000))
                    .andExpect(jsonPath("$.data.availableBalance").value(400000))
                    .andExpect(jsonPath("$.data.bankAccount.bankName").value("국민은행"));
        }

        @Test
        @DisplayName("성공 - 계좌 없이 수익만")
        void successNoAccount() throws Exception {
            RevenueOverviewResponse response = RevenueOverviewResponse.of(0, 0, 0, 0,0,null);
            given(revenueService.getRevenueOverview(1L)).willReturn(response);

            mockMvc.perform(get("/api/revenues/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.availableBalance").value(0))
                    .andExpect(jsonPath("$.data.bankAccount").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/revenues/me/statistics")
    class GetStatistics {

        @Test
        @DisplayName("성공 - 월별 통계 조회")
        void successMonthly() throws Exception {
            List<RevenueStatisticsItem> items = List.of(
                    RevenueStatisticsItem.of("2026-01", 80000, 20000),
                    RevenueStatisticsItem.of("2026-02", 120000, 30000)
            );
            RevenueStatisticsResponse response = RevenueStatisticsResponse.of("MONTHLY", 2026, 250000, items);
            given(statisticsService.getStatistics(eq(1L), eq(StatisticsPeriod.MONTHLY), eq(2026)))
                    .willReturn(response);

            mockMvc.perform(get("/api/revenues/me/statistics")
                            .param("period", "MONTHLY")
                            .param("year", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.period").value("MONTHLY"))
                    .andExpect(jsonPath("$.data.totalAmount").value(250000))
                    .andExpect(jsonPath("$.data.items.length()").value(2));
        }

        @Test
        @DisplayName("성공 - 파라미터 없으면 기본값 (MONTHLY, 현재 연도)")
        void successDefault() throws Exception {
            int currentYear = Year.now().getValue();
            RevenueStatisticsResponse response = RevenueStatisticsResponse.of("MONTHLY", currentYear, 0, List.of());
            given(statisticsService.getStatistics(eq(1L), eq(StatisticsPeriod.MONTHLY), eq(currentYear)))
                    .willReturn(response);

            mockMvc.perform(get("/api/revenues/me/statistics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.year").value(currentYear));
        }

        @Test
        @DisplayName("성공 - 주별 통계 조회")
        void successWeekly() throws Exception {
            RevenueStatisticsResponse response = RevenueStatisticsResponse.of("WEEKLY", 2026, 0, List.of());
            given(statisticsService.getStatistics(eq(1L), eq(StatisticsPeriod.WEEKLY), eq(2026)))
                    .willReturn(response);

            mockMvc.perform(get("/api/revenues/me/statistics")
                            .param("period", "WEEKLY")
                            .param("year", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.period").value("WEEKLY"));
        }
    }
}