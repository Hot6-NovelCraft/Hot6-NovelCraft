package com.example.hot6novelcraft.domain.calendar.controller;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.calendar.dto.request.ReadingRecordCreateRequest;
import com.example.hot6novelcraft.domain.calendar.dto.response.CalendarDailyResponse;
import com.example.hot6novelcraft.domain.calendar.dto.response.MonthlyStatResponse;
import com.example.hot6novelcraft.domain.calendar.dto.response.ReadingRecordCreateResponse;
import com.example.hot6novelcraft.domain.calendar.dto.response.ReadingRecordResponse;
import com.example.hot6novelcraft.domain.calendar.entity.enums.ReadingStatus;
import com.example.hot6novelcraft.domain.calendar.entity.enums.RecordSource;
import com.example.hot6novelcraft.common.exception.domain.CalendarExceptionEnum;
import com.example.hot6novelcraft.domain.calendar.service.CalendarService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalendarController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class CalendarControllerTest {

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

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CalendarService calendarService;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Mock User 설정
        User mockUser = mock(User.class);
        given(mockUser.getId()).willReturn(1L);
        given(userDetails.getUser()).willReturn(mockUser);

        // SecurityContext에 인증 정보 주입
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private ReadingRecordCreateRequest validPlatformRequest() {
        return new ReadingRecordCreateRequest(
                1L, RecordSource.PLATFORM, LocalDate.of(2026, 4, 15),
                null, null, "메모", 120, 350, ReadingStatus.READING
        );
    }

    private ReadingRecordCreateResponse mockCreateResponse() {
        return new ReadingRecordCreateResponse(
                1L, 1L, "채식주의자", RecordSource.EXTERNAL,
                LocalDate.of(2026, 4, 15), LocalDateTime.now()
        );
    }

    // ===================== POST /me/records =====================
    @Nested
    @DisplayName("독서 기록 등록 API")
    class CreateReadingRecord {

        @Test
        @DisplayName("정상 등록 - 201 반환")
        void createRecord_success() throws Exception {
            given(calendarService.createReadingRecord(any(), any()))
                    .willReturn(mockCreateResponse());

            mockMvc.perform(post("/api/calendars/me/records")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validPlatformRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("독서 기록이 성공적으로 등록되었습니다"))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("source 누락 시 400 반환")
        void createRecord_missingSource() throws Exception {
            var invalidRequest = new ReadingRecordCreateRequest(
                    1L, null, LocalDate.of(2026, 4, 15),
                    null, null, "메모", 120, 350, ReadingStatus.READING
            );

            mockMvc.perform(post("/api/calendars/me/records")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("readingStatus 누락 시 400 반환")
        void createRecord_missingReadingStatus() throws Exception {
            var invalidRequest = new ReadingRecordCreateRequest(
                    1L, RecordSource.PLATFORM, LocalDate.of(2026, 4, 15),
                    null, null, "메모", 120, 350, null
            );

            mockMvc.perform(post("/api/calendars/me/records")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("서재 미등록 도서 - 404 반환")
        void createRecord_notInLibrary() throws Exception {
            given(calendarService.createReadingRecord(any(), any()))
                    .willThrow(new ServiceErrorException(CalendarExceptionEnum.BOOK_NOT_IN_LIBRARY));

            mockMvc.perform(post("/api/calendars/me/records")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validPlatformRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("서재에 등록되지 않은 도서입니다"));
        }
    }

    // ===================== GET /me/records =====================
    @Nested
    @DisplayName("독서 기록 조회 API")
    class GetReadingRecords {

        @Test
        @DisplayName("전체 조회 - 200 반환")
        void getRecords_success() throws Exception {
            var pageResponse = new PageResponse<>(
                    List.of(new ReadingRecordResponse(
                            1L, null, "채식주의자", "한강",
                            RecordSource.EXTERNAL, LocalDate.of(2026, 4, 15), "메모"
                    )), 0, 1, 1L, 10, true
            );
            given(calendarService.getReadingRecords(any(), any(), any(), any()))
                    .willReturn(pageResponse);

            mockMvc.perform(get("/api/calendars/me/records"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].title").value("채식주의자"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("날짜 필터 조회 - 200 반환")
        void getRecords_withDateFilter() throws Exception {
            given(calendarService.getReadingRecords(any(), any(), any(), any()))
                    .willReturn(new PageResponse<>(List.of(), 0, 0, 0L, 10, true));

            mockMvc.perform(get("/api/calendars/me/records")
                            .param("date", "2026-04-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===================== GET /me =====================
    @Nested
    @DisplayName("독서 캘린더 조회 API")
    class GetCalendarRecords {

        @Test
        @DisplayName("정상 조회 - 200 반환")
        void getCalendar_success() throws Exception {
            given(calendarService.getCalendarRecords(any(), any(), any()))
                    .willReturn(List.of(
                            new CalendarDailyResponse(LocalDate.of(2026, 4, 1), 1, 2),
                            new CalendarDailyResponse(LocalDate.of(2026, 4, 2), 0, 0)
                    ));

            mockMvc.perform(get("/api/calendars/me")
                            .param("startDate", "2026-04-01")
                            .param("endDate", "2026-04-30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].novelCount").value(1));
        }

        @Test
        @DisplayName("1년 초과 범위 - 400 반환")
        void getCalendar_rangeExceeded() throws Exception {
            given(calendarService.getCalendarRecords(any(), any(), any()))
                    .willThrow(new ServiceErrorException(CalendarExceptionEnum.DATE_RANGE_TOO_LARGE));

            mockMvc.perform(get("/api/calendars/me")
                            .param("startDate", "2025-01-01")
                            .param("endDate", "2026-04-15"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("조회 범위가 너무 큽니다"));
        }

        @Test
        @DisplayName("startDate 누락 시 400 반환")
        void getCalendar_missingStartDate() throws Exception {
            mockMvc.perform(get("/api/calendars/me")
                            .param("endDate", "2026-04-30"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===================== GET /me/statistics =====================
    @Nested
    @DisplayName("월간 통계 조회 API")
    class GetMonthlyStatistics {

        @Test
        @DisplayName("정상 조회 - 200 반환")
        void getStatistics_success() throws Exception {
            given(calendarService.getMonthlyStatistics(any(), anyInt(), anyInt()))
                    .willReturn(new MonthlyStatResponse(
                            1250, 3, 41.6,
                            LocalDate.of(2026, 4, 10), 22
                    ));

            mockMvc.perform(get("/api/calendars/me/statistics")
                            .param("year", "2026")
                            .param("month", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalReadPages").value(1250))
                    .andExpect(jsonPath("$.data.completedBooks").value(3))
                    .andExpect(jsonPath("$.data.readingDaysCount").value(22));
        }

        @Test
        @DisplayName("미래 달 조회 - 400 반환")
        void getStatistics_futureMonth() throws Exception {
            given(calendarService.getMonthlyStatistics(any(), anyInt(), anyInt()))
                    .willThrow(new ServiceErrorException(CalendarExceptionEnum.INVALID_STAT_DATE));

            mockMvc.perform(get("/api/calendars/me/statistics")
                            .param("year", "2027")
                            .param("month", "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("유효하지 않은 날짜 범위입니다"));
        }

        @Test
        @DisplayName("year 파라미터 누락 시 400 반환")
        void getStatistics_missingYear() throws Exception {
            mockMvc.perform(get("/api/calendars/me/statistics")
                            .param("month", "4"))
                    .andExpect(status().isBadRequest());
        }
    }
}
