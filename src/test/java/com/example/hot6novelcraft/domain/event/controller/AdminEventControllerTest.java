package com.example.hot6novelcraft.domain.event.controller;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EventExceptionEnum;
import com.example.hot6novelcraft.domain.event.dto.request.EventCreateRequest;
import com.example.hot6novelcraft.domain.event.dto.response.EventDetailResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventParticipantResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventSummaryResponse;
import com.example.hot6novelcraft.domain.event.entity.enums.EventStatus;
import com.example.hot6novelcraft.domain.event.service.AdminEventService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.service.UserCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminEventController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminEventControllerTest {

    @MockBean private com.example.hot6novelcraft.common.security.JwtUtil jwtUtil;
    @MockBean private com.example.hot6novelcraft.common.security.RedisUtil redisUtil;
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockBean private UserCacheService userCacheService;
    @MockBean private UserDetailsImpl userDetails;
    @MockBean private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;
    @MockBean private AdminEventService adminEventService;

    @Autowired private MockMvc mockMvc;

    private ObjectMapper objectMapper;
    private final Long ADMIN_ID = 1L;
    private final Long EVENT_ID = 10L;
    private final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        User mockUser = mock(User.class);
        given(mockUser.getId()).willReturn(ADMIN_ID);
        given(userDetails.getUser()).willReturn(mockUser);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private EventCreateRequest validRequest() {
        return new EventCreateRequest(
                "신규 회원 환영 이벤트",
                "선착순 100명에게 포인트를 드립니다",
                5000L, 100L,
                NOW.minusDays(1), NOW.plusDays(30)
        );
    }

    private EventDetailResponse mockDetailResponse() {
        return new EventDetailResponse(
                EVENT_ID, "신규 회원 환영 이벤트", "설명",
                5000L, 100L,
                NOW.minusDays(1), NOW.plusDays(30), EventStatus.ONGOING
        );
    }

    // ===================== POST /api/admin/events =====================
    @Nested
    @DisplayName("이벤트 생성 API")
    class CreateEvent {

        @Test
        @DisplayName("정상 생성 - 201 반환")
        void createEvent_success() throws Exception {
            given(adminEventService.createEvent(any(), any())).willReturn(mockDetailResponse());

            mockMvc.perform(post("/api/admin/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("이벤트가 성공적으로 생성되었습니다"))
                    .andExpect(jsonPath("$.data.id").value(EVENT_ID))
                    .andExpect(jsonPath("$.data.title").value("신규 회원 환영 이벤트"));
        }

        @Test
        @DisplayName("제목 누락 - 400 반환")
        void createEvent_missingTitle() throws Exception {
            var invalid = new EventCreateRequest(
                    null, "설명", 5000L, 100L,
                    NOW.minusDays(1), NOW.plusDays(30)
            );

            mockMvc.perform(post("/api/admin/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("rewardPoints 누락 - 400 반환")
        void createEvent_missingRewardPoints() throws Exception {
            var invalid = new EventCreateRequest(
                    "제목", "설명", null, 100L,
                    NOW.minusDays(1), NOW.plusDays(30)
            );

            mockMvc.perform(post("/api/admin/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("maxParticipants 누락 - 400 반환")
        void createEvent_missingMaxParticipants() throws Exception {
            var invalid = new EventCreateRequest(
                    "제목", "설명", 5000L, null,
                    NOW.minusDays(1), NOW.plusDays(30)
            );

            mockMvc.perform(post("/api/admin/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("시작일이 종료일보다 늦을 때 - 400 반환")
        void createEvent_invalidPeriod() throws Exception {
            var invalid = new EventCreateRequest(
                    "제목", "설명", 5000L, 100L,
                    NOW.plusDays(30), NOW.minusDays(1)
            );

            mockMvc.perform(post("/api/admin/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===================== GET /api/admin/events =====================
    @Nested
    @DisplayName("이벤트 목록 조회 API (관리자)")
    class GetEventList {

        @Test
        @DisplayName("ONGOING 필터 조회 - 200 반환")
        void getEventList_ongoing() throws Exception {
            EventSummaryResponse summary = new EventSummaryResponse(
                    EVENT_ID, "이벤트", 5000L, 100L,
                    NOW.minusDays(1), NOW.plusDays(30), EventStatus.ONGOING
            );
            PageResponse<EventSummaryResponse> pageResponse = PageResponse.register(
                    new PageImpl<>(List.of(summary)));
            given(adminEventService.getEventList(eq(EventStatus.ONGOING), any()))
                    .willReturn(new PageImpl<>(List.of(summary)));

            mockMvc.perform(get("/api/admin/events")
                            .param("status", "ONGOING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(EVENT_ID));
        }

        @Test
        @DisplayName("결과 없을 경우 빈 목록 반환")
        void getEventList_empty() throws Exception {
            given(adminEventService.getEventList(any(), any()))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/admin/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }
    }

    // ===================== GET /api/admin/events/{eventId} =====================
    @Nested
    @DisplayName("이벤트 상세 조회 API (관리자)")
    class GetEventDetail {

        @Test
        @DisplayName("정상 조회 - 200 반환")
        void getEventDetail_success() throws Exception {
            given(adminEventService.getEventDetail(EVENT_ID)).willReturn(mockDetailResponse());

            mockMvc.perform(get("/api/admin/events/{eventId}", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(EVENT_ID))
                    .andExpect(jsonPath("$.data.status").value("ONGOING"));
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 - 404 반환")
        void getEventDetail_notFound() throws Exception {
            given(adminEventService.getEventDetail(EVENT_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_NOT_FOUND));

            mockMvc.perform(get("/api/admin/events/{eventId}", EVENT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(EventExceptionEnum.EVENT_NOT_FOUND.getMessage()));
        }
    }

    // ===================== GET /api/admin/events/{eventId}/participants =====================
    @Nested
    @DisplayName("이벤트 참여자 목록 조회 API")
    class GetParticipants {

        @Test
        @DisplayName("정상 조회 - 200 반환")
        void getParticipants_success() throws Exception {
            EventParticipantResponse participant = new EventParticipantResponse(1L, 2L, NOW);
            given(adminEventService.getParticipants(eq(EVENT_ID), any()))
                    .willReturn(new PageImpl<>(List.of(participant)));

            mockMvc.perform(get("/api/admin/events/{eventId}/participants", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].userId").value(2));
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 참여자 조회 - 404 반환")
        void getParticipants_eventNotFound() throws Exception {
            given(adminEventService.getParticipants(eq(EVENT_ID), any()))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_NOT_FOUND));

            mockMvc.perform(get("/api/admin/events/{eventId}/participants", EVENT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("참여자 없을 경우 빈 목록 반환")
        void getParticipants_empty() throws Exception {
            given(adminEventService.getParticipants(eq(EVENT_ID), any()))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/admin/events/{eventId}/participants", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }
    }
}