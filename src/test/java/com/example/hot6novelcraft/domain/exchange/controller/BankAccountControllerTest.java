package com.example.hot6novelcraft.domain.exchange.controller;

import com.example.hot6novelcraft.domain.exchange.dto.request.BankAccountCreateRequest;
import com.example.hot6novelcraft.domain.exchange.dto.request.VerificationConfirmRequest;
import com.example.hot6novelcraft.domain.exchange.dto.response.BankAccountVerifyResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.VerificationConfirmResponse;
import com.example.hot6novelcraft.domain.exchange.service.BankAccountService;
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
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BankAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class BankAccountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean BankAccountService bankAccountService;
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
    @DisplayName("POST /api/revenues/me/account/verify")
    class RequestVerification {

        @Test
        @DisplayName("성공 - 201 Created")
        void success() throws Exception {
            given(bankAccountService.registerAndRequestVerification(eq(1L), any(BankAccountCreateRequest.class)))
                    .willReturn(new BankAccountVerifyResponse(
                            1L, "국민은행", "******7890", "홍길동", LocalDateTime.now().plusMinutes(5)));

            mockMvc.perform(post("/api/revenues/me/account/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new BankAccountCreateRequest("국민은행", "1234567890", "홍길동"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("인증 코드가 발송되었습니다"))
                    .andExpect(jsonPath("$.data.bankName").value("국민은행"));
        }

        @Test
        @DisplayName("실패 - 은행명 미입력 400")
        void failBlankBankName() throws Exception {
            mockMvc.perform(post("/api/revenues/me/account/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new BankAccountCreateRequest("", "1234567890", "홍길동"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 계좌번호 형식 불량 400")
        void failInvalidAccountNumber() throws Exception {
            mockMvc.perform(post("/api/revenues/me/account/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new BankAccountCreateRequest("국민은행", "abc", "홍길동"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 예금주 미입력 400")
        void failBlankHolder() throws Exception {
            mockMvc.perform(post("/api/revenues/me/account/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new BankAccountCreateRequest("국민은행", "1234567890", ""))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/revenues/me/account/verify/confirm")
    class ConfirmVerification {

        @Test
        @DisplayName("성공 - 인증 완료")
        void successVerified() throws Exception {
            given(bankAccountService.confirmVerification(eq(1L), any(VerificationConfirmRequest.class)))
                    .willReturn(VerificationConfirmResponse.success(1L));

            mockMvc.perform(post("/api/revenues/me/account/verify/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new VerificationConfirmRequest(1L, "1234"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isVerified").value(true))
                    .andExpect(jsonPath("$.message").value("계좌 인증이 완료되었습니다"));
        }

        @Test
        @DisplayName("성공 - 코드 불일치 (남은 횟수 반환)")
        void successNotVerified() throws Exception {
            given(bankAccountService.confirmVerification(eq(1L), any(VerificationConfirmRequest.class)))
                    .willReturn(VerificationConfirmResponse.fail(1L, 4));

            mockMvc.perform(post("/api/revenues/me/account/verify/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new VerificationConfirmRequest(1L, "9999"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isVerified").value(false))
                    .andExpect(jsonPath("$.data.remainingAttempts").value(4));
        }

        @Test
        @DisplayName("실패 - 인증코드 형식 불량 400")
        void failInvalidCode() throws Exception {
            mockMvc.perform(post("/api/revenues/me/account/verify/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new VerificationConfirmRequest(1L, "abc"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - bankAccountId null 400")
        void failNullAccountId() throws Exception {
            mockMvc.perform(post("/api/revenues/me/account/verify/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bankAccountId\": null, \"verificationCode\": \"1234\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}