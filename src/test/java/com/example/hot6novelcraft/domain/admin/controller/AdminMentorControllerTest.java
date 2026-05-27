package com.example.hot6novelcraft.domain.admin.controller;

import com.example.hot6novelcraft.common.config.TestSecurityConfig;
import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.domain.admin.dto.request.AdminMentorRejectRequest;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminPendingMentorResponse;
import com.example.hot6novelcraft.domain.admin.service.AdminMentorService;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMentorController.class)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
@AutoConfigureMockMvc(addFilters = false) // JWT 필터 비활성화
@DisplayName("AdminMentorController 컨트롤러 테스트")
class AdminMentorControllerTest {

    // JWT 관련 Bean Mock 등록 (컨텍스트 로드 오류 방지)
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
    private AdminMentorService adminMentorService;

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
                        List.of(new SimpleGrantedAuthority(authority)) // 권한 주입
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
        @DisplayName("ADMIN 권한으로 대기 목록 조회 성공 - 200")
        void getPendingMentors_ADMIN권한_200() throws Exception {
            // given
            setAuth("ADMIN");

            // record 직접 생성
            AdminPendingMentorResponse mockResponse = new AdminPendingMentorResponse(
                    1L, 1L, "mentor@test.com", "멘토닉네임",
                    CareerLevel.PROFICIENT, MentorStatus.PENDING, "수상경력 없음"
            );

            given(adminMentorService.getPendingProficientMentors())
                    .willReturn(List.of(mockResponse));

            // when & then
            mockMvc.perform(get("/api/admin/mentors/pending"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.message").value("숙련 (PROFICIENT) 등급 심사 대기 목록 조회 완료"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].mentorId").value(1))
                    .andExpect(jsonPath("$.data[0].email").value("mentor@test.com"));
        }

        @Test
        @DisplayName("SUPER_ADMIN 권한으로 대기 목록 조회 성공 - 200")
        void getPendingMentors_SUPER_ADMIN권한_200() throws Exception {
            // given
            setAuth("SUPER_ADMIN");
            given(adminMentorService.getPendingProficientMentors())
                    .willReturn(List.of());

            // when & then
            mockMvc.perform(get("/api/admin/mentors/pending"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN 권한으로 멘토 승인 성공 - 200")
        void approveMentor_ADMIN권한_200() throws Exception {
            setAuth("ADMIN");
            doNothing().when(adminMentorService).approveMentor(anyLong());

            mockMvc.perform(patch("/api/admin/mentors/{mentorId}/approve", 1L))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))     // ← code → status
                    .andExpect(jsonPath("$.message").value("멘토 등급 심사 승급 완료"));
        }

        @Test
        @DisplayName("ADMIN 권한으로 멘토 거절 성공 - 200")
        void rejectMentor_ADMIN권한_200() throws Exception {
            setAuth("ADMIN");
            AdminMentorRejectRequest request = new AdminMentorRejectRequest("경력 사항 미흡");
            doNothing().when(adminMentorService).rejectMentor(anyLong(), any());

            mockMvc.perform(patch("/api/admin/mentors/{mentorId}/reject", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))     // ← code → status
                    .andExpect(jsonPath("$.message").value("멘토 등급 심사 승급 거절"));
        }

        // 인증 없는 케이스 수정
        @Test
        @DisplayName("인증 없이 대기 목록 조회 - 403")
        void getPendingMentors_인증없음_403() throws Exception {
            // given - SecurityContext 비움
            SecurityContextHolder.clearContext();

            // when & then
            // addFilters = false 상태에서 인증 없으면 anonymousUser로 처리
            // → @PreAuthorize 에서 권한 없으므로 403
            mockMvc.perform(get("/api/admin/mentors/pending"))
                    .andDo(print())
                    .andExpect(status().isForbidden()); // 403
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
        void getPendingMentors_인증없음_403() throws Exception {
            // given - SecurityContext 비움
            SecurityContextHolder.clearContext();

            // when & then
            mockMvc.perform(get("/api/admin/mentors/pending"))
                    .andDo(print())
                    .andExpect(status().isForbidden()); // addFilters = false라 403으로 떨어짐
        }

        @Test
        @DisplayName("READER 권한으로 대기 목록 조회 - 403")
        void getPendingMentors_READER권한_403() throws Exception {
            // given
            setAuth("READER");

            // when & then
            mockMvc.perform(get("/api/admin/mentors/pending"))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN 권한으로 멘토 승인 시 멘토 없으면 - 404")
        void approveMentor_멘토없음_404() throws Exception {
            // given
            setAuth("ADMIN");
            doThrow(new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND))
                    .when(adminMentorService).approveMentor(1L);

            // when & then
            mockMvc.perform(patch("/api/admin/mentors/{mentorId}/approve", 1L))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }
}