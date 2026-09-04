package com.example.hot6novelcraft.domain.event.controller;

import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EventExceptionEnum;
import com.example.hot6novelcraft.domain.event.dto.response.EventDetailResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventParticipateResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventSummaryResponse;
import com.example.hot6novelcraft.domain.event.entity.enums.EventStatus;
import com.example.hot6novelcraft.domain.event.service.UserEventService;
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

@WebMvcTest(UserEventController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class UserEventControllerTest {

    @MockBean private com.example.hot6novelcraft.common.security.JwtUtil jwtUtil;
    @MockBean private com.example.hot6novelcraft.common.security.RedisUtil redisUtil;
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockBean private UserCacheService userCacheService;
    @MockBean private UserDetailsImpl userDetails;
    @MockBean private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;
    @MockBean private UserEventService userEventService;

    @Autowired private MockMvc mockMvc;

    private ObjectMapper objectMapper;
    private final Long USER_ID = 1L;
    private final Long EVENT_ID = 10L;
    private final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        User mockUser = mock(User.class);
        given(mockUser.getId()).willReturn(USER_ID);
        given(userDetails.getUser()).willReturn(mockUser);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private EventDetailResponse mockDetailResponse(EventStatus status) {
        return new EventDetailResponse(
                EVENT_ID, "테스트 이벤트", "설명",
                5000L, 100L,
                NOW.minusDays(1), NOW.plusDays(30), status
        );
    }

    // ===================== GET /api/events =====================
    @Nested
    @DisplayName("이벤트 목록 조회 API (사용자)")
    class GetEventList {

        @Test
        @DisplayName("ONGOING 필터 조회 - 200 반환")
        void getEventList_ongoing() throws Exception {
            EventSummaryResponse summary = new EventSummaryResponse(
                    EVENT_ID, "테스트 이벤트", 5000L, 100L,
                    NOW.minusDays(1), NOW.plusDays(30), EventStatus.ONGOING
            );
            given(userEventService.getEventList(eq(EventStatus.ONGOING), any()))
                    .willReturn(new PageImpl<>(List.of(summary)));

            mockMvc.perform(get("/api/events")
                            .param("status", "ONGOING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(EVENT_ID))
                    .andExpect(jsonPath("$.data.content[0].status").value("ONGOING"));
        }

        @Test
        @DisplayName("ENDED 필터 조회 - 200 반환")
        void getEventList_ended() throws Exception {
            given(userEventService.getEventList(eq(EventStatus.ENDED), any()))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/events")
                            .param("status", "ENDED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }

        @Test
        @DisplayName("기본값 ONGOING으로 조회 - 200 반환")
        void getEventList_defaultStatus() throws Exception {
            given(userEventService.getEventList(eq(EventStatus.ONGOING), any()))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("결과 없을 경우 빈 목록 반환")
        void getEventList_empty() throws Exception {
            given(userEventService.getEventList(any(), any()))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    // ===================== GET /api/events/{eventId} =====================
    @Nested
    @DisplayName("이벤트 상세 조회 API (사용자)")
    class GetEventDetail {

        @Test
        @DisplayName("진행 중 이벤트 상세 조회 - 200 반환")
        void getEventDetail_ongoing() throws Exception {
            given(userEventService.getEventDetail(EVENT_ID))
                    .willReturn(mockDetailResponse(EventStatus.ONGOING));

            mockMvc.perform(get("/api/events/{eventId}", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(EVENT_ID))
                    .andExpect(jsonPath("$.data.status").value("ONGOING"))
                    .andExpect(jsonPath("$.data.rewardPoints").value(5000));
        }

        @Test
        @DisplayName("종료된 이벤트 상세 조회 - 200 반환")
        void getEventDetail_ended() throws Exception {
            given(userEventService.getEventDetail(EVENT_ID))
                    .willReturn(mockDetailResponse(EventStatus.ENDED));

            mockMvc.perform(get("/api/events/{eventId}", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ENDED"));
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 - 404 반환")
        void getEventDetail_notFound() throws Exception {
            given(userEventService.getEventDetail(EVENT_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_NOT_FOUND));

            mockMvc.perform(get("/api/events/{eventId}", EVENT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(EventExceptionEnum.EVENT_NOT_FOUND.getMessage()));
        }
    }

    // ===================== POST /api/events/{eventId}/participants =====================
    @Nested
    @DisplayName("이벤트 참여 신청 API")
    class Participate {

        @Test
        @DisplayName("정상 참여 신청 - 201 반환")
        void participate_success() throws Exception {
            EventParticipateResponse response = new EventParticipateResponse(
                    EVENT_ID, USER_ID, 5000L, NOW
            );
            given(userEventService.participate(EVENT_ID, USER_ID)).willReturn(response);

            mockMvc.perform(post("/api/events/{eventId}/participants", EVENT_ID))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("이벤트 참여 신청이 완료되었습니다"))
                    .andExpect(jsonPath("$.data.eventId").value(EVENT_ID))
                    .andExpect(jsonPath("$.data.rewardPoints").value(5000));
        }

        @Test
        @DisplayName("진행 중이 아닌 이벤트 참여 - 400 반환")
        void participate_notOngoing() throws Exception {
            given(userEventService.participate(EVENT_ID, USER_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_NOT_ONGOING));

            mockMvc.perform(post("/api/events/{eventId}/participants", EVENT_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(EventExceptionEnum.EVENT_NOT_ONGOING.getMessage()));
        }

        @Test
        @DisplayName("중복 참여 시도 - 409 반환")
        void participate_alreadyParticipated() throws Exception {
            given(userEventService.participate(EVENT_ID, USER_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_ALREADY_PARTICIPATED));

            mockMvc.perform(post("/api/events/{eventId}/participants", EVENT_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(EventExceptionEnum.EVENT_ALREADY_PARTICIPATED.getMessage()));
        }

        @Test
        @DisplayName("선착순 마감 - 409 반환")
        void participate_full() throws Exception {
            given(userEventService.participate(EVENT_ID, USER_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_PARTICIPANTS_FULL));

            mockMvc.perform(post("/api/events/{eventId}/participants", EVENT_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(EventExceptionEnum.EVENT_PARTICIPANTS_FULL.getMessage()));
        }

        @Test
        @DisplayName("동시 요청 초과 - 429 반환")
        void participate_tooManyRequests() throws Exception {
            given(userEventService.participate(EVENT_ID, USER_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_LOCK_ACQUIRE_FAILED));

            mockMvc.perform(post("/api/events/{eventId}/participants", EVENT_ID))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(EventExceptionEnum.EVENT_LOCK_ACQUIRE_FAILED.getMessage()));
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 참여 - 404 반환")
        void participate_notFound() throws Exception {
            given(userEventService.participate(EVENT_ID, USER_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_NOT_FOUND));

            mockMvc.perform(post("/api/events/{eventId}/participants", EVENT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}