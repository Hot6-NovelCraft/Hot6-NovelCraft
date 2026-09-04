package com.example.hot6novelcraft.domain.calendar.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.calendar.dto.request.ReadingRecordCreateRequest;
import com.example.hot6novelcraft.domain.calendar.dto.response.ReadingRecordCreateResponse;
import com.example.hot6novelcraft.domain.calendar.entity.*;
import com.example.hot6novelcraft.common.exception.domain.CalendarExceptionEnum;
import com.example.hot6novelcraft.domain.calendar.entity.enums.ReadingStatus;
import com.example.hot6novelcraft.domain.calendar.entity.enums.RecordSource;
import com.example.hot6novelcraft.domain.library.entity.enums.LibraryType;
import com.example.hot6novelcraft.domain.library.repository.LibraryRepository;
import com.example.hot6novelcraft.domain.calendar.repository.ReadingRecordRepository;
import com.example.hot6novelcraft.domain.library.entity.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @InjectMocks
    private CalendarService calendarService;

    @Mock
    private ReadingRecordRepository readingRecordRepository;

    @Mock
    private LibraryRepository libraryRepository;

    // 공통 픽스처
    private final Long USER_ID = 1L;
    private final Long NOVEL_ID = 1L;
    private final LocalDate READ_DATE = LocalDate.of(2026, 4, 15);

    private ReadingRecordCreateRequest platformRequest() {
        return new ReadingRecordCreateRequest(
                NOVEL_ID, RecordSource.PLATFORM, READ_DATE,
                null, null, "오늘 3회차까지 정독 완료",
                120, 350, ReadingStatus.READING
        );
    }

    private ReadingRecordCreateRequest externalRequest() {
        return new ReadingRecordCreateRequest(
                null, RecordSource.EXTERNAL, READ_DATE,
                "채식주의자", "한강", "인상적인 작품",
                200, 247, ReadingStatus.COMPLETED
        );
    }

    private ReadingRecord mockRecord(RecordSource source) {
        return ReadingRecord.create(
                USER_ID, source == RecordSource.PLATFORM ? NOVEL_ID : null,
                source == RecordSource.EXTERNAL ? "채식주의자" : null,
                source == RecordSource.EXTERNAL ? "한강" : null,
                source, READ_DATE, "메모", 120, 350, ReadingStatus.READING
        );
    }

    // ===================== createReadingRecord =====================
    @Nested
    @DisplayName("독서 기록 등록")
    class CreateReadingRecord {

        @Test
        @DisplayName("PLATFORM 소설 - 서재 등록된 경우 성공")
        void createPlatformRecord_success() {
            given(libraryRepository.findByUserIdAndNovelId(USER_ID, NOVEL_ID))
                    .willReturn(Optional.of(Library.create(USER_ID, NOVEL_ID, LibraryType.READING, "테스트소설", "작가명", "https://cover.png")));
            given(readingRecordRepository.save(any())).willReturn(mockRecord(RecordSource.PLATFORM));

            ReadingRecordCreateResponse response = calendarService.createReadingRecord(USER_ID, platformRequest());

            assertThat(response).isNotNull();
            assertThat(response.source()).isEqualTo(RecordSource.PLATFORM);
            then(libraryRepository).should().findByUserIdAndNovelId(USER_ID, NOVEL_ID);
        }

        @Test
        @DisplayName("PLATFORM 소설 - 서재 미등록 시 예외 발생")
        void createPlatformRecord_notInLibrary() {
            given(libraryRepository.findByUserIdAndNovelId(USER_ID, NOVEL_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> calendarService.createReadingRecord(USER_ID, platformRequest()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(CalendarExceptionEnum.BOOK_NOT_IN_LIBRARY.getMessage());

            then(readingRecordRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("EXTERNAL 도서 - 서재 검증 없이 등록 성공")
        void createExternalRecord_success() {
            given(readingRecordRepository.save(any())).willReturn(mockRecord(RecordSource.EXTERNAL));

            ReadingRecordCreateResponse response = calendarService.createReadingRecord(USER_ID, externalRequest());

            assertThat(response).isNotNull();
            then(libraryRepository).should(never()).findByUserIdAndNovelId(any(), any());
        }
    }

    // ===================== getReadingRecords =====================
    @Nested
    @DisplayName("독서 기록 조회")
    class GetReadingRecords {

        @Test
        @DisplayName("전체 조회 성공")
        void getReadingRecords_noFilter() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<ReadingRecord> page = new PageImpl<>(List.of(mockRecord(RecordSource.EXTERNAL)));
            given(readingRecordRepository.findByCondition(USER_ID, null, null, pageable))
                    .willReturn(page);

            var response = calendarService.getReadingRecords(USER_ID, null, null, pageable);

            assertThat(response.content()).hasSize(1);
            assertThat(response.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("날짜 필터 조회 성공")
        void getReadingRecords_withDateFilter() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<ReadingRecord> page = new PageImpl<>(List.of(mockRecord(RecordSource.EXTERNAL)));
            given(readingRecordRepository.findByCondition(USER_ID, READ_DATE, null, pageable))
                    .willReturn(page);

            var response = calendarService.getReadingRecords(USER_ID, READ_DATE, null, pageable);

            assertThat(response.content()).hasSize(1);
        }

        @Test
        @DisplayName("novelId 필터 조회 성공")
        void getReadingRecords_withNovelIdFilter() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<ReadingRecord> page = new PageImpl<>(List.of(mockRecord(RecordSource.PLATFORM)));
            given(readingRecordRepository.findByCondition(USER_ID, null, NOVEL_ID, pageable))
                    .willReturn(page);

            var response = calendarService.getReadingRecords(USER_ID, null, NOVEL_ID, pageable);

            assertThat(response.content()).hasSize(1);
        }

        @Test
        @DisplayName("결과 없을 경우 빈 리스트 반환")
        void getReadingRecords_empty() {
            PageRequest pageable = PageRequest.of(0, 10);
            given(readingRecordRepository.findByCondition(any(), any(), any(), any()))
                    .willReturn(Page.empty());

            var response = calendarService.getReadingRecords(USER_ID, null, null, pageable);

            assertThat(response.content()).isEmpty();
            assertThat(response.totalElements()).isZero();
        }
    }

    // ===================== getCalendarRecords =====================
    @Nested
    @DisplayName("독서 캘린더 조회")
    class GetCalendarRecords {

        @Test
        @DisplayName("정상 범위 조회 성공")
        void getCalendarRecords_success() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 4, 30);
            given(readingRecordRepository.findByUserIdAndDateBetween(USER_ID, start, end))
                    .willReturn(List.of(mockRecord(RecordSource.EXTERNAL)));

            var response = calendarService.getCalendarRecords(USER_ID, start, end);

            assertThat(response).hasSize(30); // 4월 30일치 전부 반환
            assertThat(response.stream().filter(r -> r.novelCount() > 0 || r.episodeCount() > 0))
                    .isNotEmpty();
        }

        @Test
        @DisplayName("1년 초과 범위 조회 시 예외 발생")
        void getCalendarRecords_rangeExceeded() {
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2026, 4, 15);

            assertThatThrownBy(() -> calendarService.getCalendarRecords(USER_ID, start, end))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(CalendarExceptionEnum.DATE_RANGE_TOO_LARGE.getMessage());
        }

        @Test
        @DisplayName("기록 없는 날은 0으로 반환")
        void getCalendarRecords_emptyDays() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 4, 3);
            given(readingRecordRepository.findByUserIdAndDateBetween(USER_ID, start, end))
                    .willReturn(List.of());

            var response = calendarService.getCalendarRecords(USER_ID, start, end);

            assertThat(response).hasSize(3);
            assertThat(response).allMatch(r -> r.novelCount() == 0 && r.episodeCount() == 0);
        }
    }

    // ===================== getMonthlyStatistics =====================
    @Nested
    @DisplayName("월간 통계 조회")
    class GetMonthlyStatistics {

        @Test
        @DisplayName("정상 통계 조회 성공")
        void getMonthlyStatistics_success() {
            given(readingRecordRepository.findByUserIdAndYearAndMonth(USER_ID, 2026, 4))
                    .willReturn(List.of(
                            ReadingRecord.create(USER_ID, null, "책A", "작가A",
                                    RecordSource.EXTERNAL, READ_DATE, "메모", 100, 300, ReadingStatus.READING),
                            ReadingRecord.create(USER_ID, null, "책B", "작가B",
                                    RecordSource.EXTERNAL, READ_DATE, "메모", 200, 200, ReadingStatus.COMPLETED)
                    ));

            var response = calendarService.getMonthlyStatistics(USER_ID, 2026, 4);

            assertThat(response.totalReadPages()).isEqualTo(300);
            assertThat(response.completedBooks()).isEqualTo(1);
            assertThat(response.readingDaysCount()).isEqualTo(1);
            assertThat(response.mostReadDay()).isEqualTo(READ_DATE);
        }

        @Test
        @DisplayName("미래 달 조회 시 예외 발생")
        void getMonthlyStatistics_futureMonth() {
            assertThatThrownBy(() -> calendarService.getMonthlyStatistics(USER_ID, 2027, 1))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(CalendarExceptionEnum.INVALID_STAT_DATE.getMessage());
        }

        @Test
        @DisplayName("기록 없을 경우 0으로 반환")
        void getMonthlyStatistics_noRecords() {
            given(readingRecordRepository.findByUserIdAndYearAndMonth(USER_ID, 2026, 4))
                    .willReturn(List.of());

            var response = calendarService.getMonthlyStatistics(USER_ID, 2026, 4);

            assertThat(response.totalReadPages()).isZero();
            assertThat(response.completedBooks()).isZero();
            assertThat(response.readingDaysCount()).isZero();
            assertThat(response.mostReadDay()).isNull();
        }
    }
}
