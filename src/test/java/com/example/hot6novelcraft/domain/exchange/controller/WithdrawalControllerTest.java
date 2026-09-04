package com.example.hot6novelcraft.domain.exchange.controller;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.exchange.dto.request.WithdrawalCreateRequest;
import com.example.hot6novelcraft.domain.exchange.dto.response.BankAccountInfoResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalDetailResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalResponse;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import com.example.hot6novelcraft.domain.exchange.service.WithdrawalService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.service.UserCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WithdrawalController.class)
@AutoConfigureMockMvc(addFilters = false)
class WithdrawalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean WithdrawalService withdrawalService;
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

    private WithdrawalResponse createResponse(Long id) {
        return new WithdrawalResponse(id, 50000, 1650, 48350,
                WithdrawalStatus.PENDING, null, LocalDateTime.now(), null);
    }

    @Nested
    @DisplayName("POST /api/revenues/me/exchanges")
    class Create {

        @Test
        @DisplayName("성공 - 201 Created")
        void success() throws Exception {
            given(withdrawalService.createWithdrawal(eq(1L), any(WithdrawalCreateRequest.class)))
                    .willReturn(createResponse(1L));

            mockMvc.perform(post("/api/revenues/me/exchanges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new WithdrawalCreateRequest(50000))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("환전 신청이 완료되었습니다"))
                    .andExpect(jsonPath("$.data.requestAmount").value(50000))
                    .andExpect(jsonPath("$.data.fee").value(1650))
                    .andExpect(jsonPath("$.data.actualAmount").value(48350))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("실패 - 금액 null 400")
        void failNullAmount() throws Exception {
            mockMvc.perform(post("/api/revenues/me/exchanges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"requestAmount\": null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 금액 0 이하 400")
        void failZeroAmount() throws Exception {
            mockMvc.perform(post("/api/revenues/me/exchanges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new WithdrawalCreateRequest(0))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/revenues/me/exchanges")
    class GetList {

        @Test
        @DisplayName("성공 - 전체 목록 조회")
        void success() throws Exception {
            PageResponse<WithdrawalResponse> pageResponse = PageResponse.register(
                    new PageImpl<>(List.of(createResponse(1L), createResponse(2L)), PageRequest.of(0, 10), 2)
            );
            given(withdrawalService.getWithdrawals(eq(1L), any(), any(), any(), any()))
                    .willReturn(pageResponse);

            mockMvc.perform(get("/api/revenues/me/exchanges"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("성공 - 상태 필터 적용")
        void successWithStatusFilter() throws Exception {
            PageResponse<WithdrawalResponse> pageResponse = PageResponse.register(
                    new PageImpl<>(List.of(createResponse(1L)), PageRequest.of(0, 10), 1)
            );
            given(withdrawalService.getWithdrawals(eq(1L), eq(WithdrawalStatus.PENDING), any(), any(), any()))
                    .willReturn(pageResponse);

            mockMvc.perform(get("/api/revenues/me/exchanges").param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1));
        }

        @Test
        @DisplayName("성공 - 기간 필터 적용")
        void successWithDateFilter() throws Exception {
            PageResponse<WithdrawalResponse> pageResponse = PageResponse.register(
                    new PageImpl<>(List.of(), PageRequest.of(0, 10), 0)
            );
            given(withdrawalService.getWithdrawals(eq(1L), any(), any(), any(), any()))
                    .willReturn(pageResponse);

            mockMvc.perform(get("/api/revenues/me/exchanges")
                            .param("startDate", "2026-04-01T00:00:00")
                            .param("endDate", "2026-04-30T23:59:59"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/revenues/me/exchanges/{id}")
    class GetDetail {

        @Test
        @DisplayName("성공 - 환전 상세 + 계좌 정보")
        void success() throws Exception {
            BankAccountInfoResponse bankInfo = BankAccountInfoResponse.of(
                    1L, "국민은행", "******7890", "홍길동", true);
            WithdrawalDetailResponse detail = new WithdrawalDetailResponse(
                    1L, 50000, 1650, 48350, WithdrawalStatus.PENDING, null,
                    LocalDateTime.now(), null, bankInfo
            );
            given(withdrawalService.getWithdrawalDetail(1L, 1L)).willReturn(detail);

            mockMvc.perform(get("/api/revenues/me/exchanges/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.withdrawalId").value(1))
                    .andExpect(jsonPath("$.data.requestAmount").value(50000))
                    .andExpect(jsonPath("$.data.bankAccount.bankName").value("국민은행"));
        }
    }
}