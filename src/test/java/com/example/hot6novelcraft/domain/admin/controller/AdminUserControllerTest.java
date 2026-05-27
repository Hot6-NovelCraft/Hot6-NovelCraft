package com.example.hot6novelcraft.domain.admin.controller;

import com.example.hot6novelcraft.common.config.TestSecurityConfig;
import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.domain.admin.service.AdminUserService;
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
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminUserController 컨트롤러 테스트")
class AdminUserControllerTest {

    @MockBean
    private com.example.hot6novelcraft.common.security.JwtUtil jwtUtil;

    @MockBean
    private com.example.hot6novelcraft.common.security.RedisUtil redisUtil;

    @MockBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockBean
    private UserCacheService userCacheService;

    @MockBean
    private UserDetailsImpl userDetails;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // SecurityContext에 권한 주입하는 헬퍼 메서드
    private void setAuth(String authority) {
        User mockUser = mock(User.class);
        given(mockUser.getId()).willReturn(1L);
        given(userDetails.getUser()).willReturn(mockUser);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // =============================================
    // 성공 케이스
    // =============================================
    @Nested
    @DisplayName("성공 케이스")
    class SuccessCase {

        @Test
        @DisplayName("SUPER_ADMIN 권한으로 대기 목록 조회 성공 - 200")
        void getPendingAdmins_SUPER_ADMIN권한_200() throws Exception {
            setAuth("SUPER_ADMIN");
            given(adminUserService.getPendingAdmins())
                    .willReturn(List.of());

            mockMvc.perform(get("/api/admin/users/pending"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))  // ← code → status
                    .andExpect(jsonPath("$.message").value("관리자 승인 대기 목록 조회 완료"))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("SUPER_ADMIN 권한으로 관리자 승인 성공 - 200")
        void approveUser_SUPER_ADMIN권한_200() throws Exception {
            setAuth("SUPER_ADMIN");
            doNothing().when(adminUserService).approvePendingAdmin(anyLong());

            mockMvc.perform(patch("/api/admin/users/{userId}/approve", 1L))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))  // ← code → status
                    .andExpect(jsonPath("$.message").value("일반 관리자 승인 완료"));
        }

        @Test
        @DisplayName("SUPER_ADMIN 권한으로 관리자 거절 성공 - 200")
        void rejectUser_SUPER_ADMIN권한_200() throws Exception {
            setAuth("SUPER_ADMIN");
            doNothing().when(adminUserService).rejectPendingAdmin(anyLong());

            mockMvc.perform(patch("/api/admin/users/{userId}/reject", 1L))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))  // ← code → status
                    .andExpect(jsonPath("$.message").value("관리자 가입 거절 완료"));
        }
    }

    // =============================================
    // 실패 케이스
    // =============================================
    @Nested
    @DisplayName("실패 케이스")
    class FailCase {

        @Test
        @DisplayName("인증 없이 대기 목록 조회 - 403")
        void getPendingAdmins_인증없음_403() throws Exception {
            // given
            SecurityContextHolder.clearContext();

            // when & then
            mockMvc.perform(get("/api/admin/users/pending"))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN 권한으로 SUPER_ADMIN 전용 API 접근 - 403")
        void getPendingAdmins_ADMIN권한_403() throws Exception {
            // given - SUPER_ADMIN 전용인데 ADMIN으로 접근
            setAuth("ADMIN");

            // when & then
            mockMvc.perform(get("/api/admin/users/pending"))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SUPER_ADMIN 권한으로 승인 시 이미 처리된 관리자 - 400")
        void approveUser_이미처리된관리자_예외() throws Exception {
            setAuth("SUPER_ADMIN");
            // approvePendingAdmin이 예외 던지도록 설정
            doThrow(new ServiceErrorException(UserExceptionEnum.ERR_NOT_PENDING_ADMIN))
                    .when(adminUserService).approvePendingAdmin(anyLong());

            mockMvc.perform(patch("/api/admin/users/{userId}/approve", 1L))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("READER 권한으로 관리자 승인 - 403")
        void approveUser_READER권한_403() throws Exception {
            // given
            setAuth("READER");

            // when & then
            mockMvc.perform(patch("/api/admin/users/{userId}/approve", 1L))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }
}