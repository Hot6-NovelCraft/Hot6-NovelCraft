package com.example.hot6novelcraft.domain.calendar.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.CalendarExceptionEnum;
import com.example.hot6novelcraft.domain.calendar.dto.request.ReadingRecordCreateRequest;
import com.example.hot6novelcraft.domain.calendar.dto.response.CalendarDailyResponse;
import com.example.hot6novelcraft.domain.calendar.dto.response.MonthlyStatResponse;
import com.example.hot6novelcraft.domain.calendar.dto.response.ReadingRecordCreateResponse;
import com.example.hot6novelcraft.domain.calendar.dto.response.ReadingRecordResponse;
import com.example.hot6novelcraft.domain.calendar.entity.ReadingRecord;
import com.example.hot6novelcraft.domain.calendar.entity.enums.ReadingStatus;
import com.example.hot6novelcraft.domain.calendar.entity.enums.RecordSource;
import com.example.hot6novelcraft.domain.calendar.repository.ReadingRecordRepository;
import com.example.hot6novelcraft.domain.library.repository.LibraryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final ReadingRecordRepository readingRecordRepository;
    private final LibraryRepository libraryRepository;

    @Transactional
    public ReadingRecordCreateResponse createReadingRecord(Long userId, ReadingRecordCreateRequest request) {
        if (request.source() == RecordSource.PLATFORM) {
            libraryRepository.findByUserIdAndNovelId(userId, request.novelId())
                    .orElseThrow(() -> new ServiceErrorException(CalendarExceptionEnum.BOOK_NOT_IN_LIBRARY));
        }

        ReadingRecord record = ReadingRecord.create(
                userId,
                request.novelId(),
                request.title(),
                request.authorName(),
                request.source(),
                request.readDate(),
                request.note(),
                request.readPage(),
                request.totalPage(),
                request.readingStatus()
        );

        return ReadingRecordCreateResponse.from(readingRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadingRecordResponse> getReadingRecords(
            Long userId,
            LocalDate date,
            Long novelId,
            Pageable pageable
    ) {
        Page<ReadingRecordResponse> page = readingRecordRepository
                .findByCondition(userId, date, novelId, pageable)
                .map(ReadingRecordResponse::from);

        return PageResponse.register(page);
    }

    @Transactional(readOnly = true)
    public List<CalendarDailyResponse> getCalendarRecords(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 1년 초과 조회 방지
        if (startDate.plusYears(1).isBefore(endDate) || startDate.plusYears(1).isEqual(endDate)) {
            throw new ServiceErrorException(CalendarExceptionEnum.DATE_RANGE_TOO_LARGE);
        }

        List<ReadingRecord> records = readingRecordRepository
                .findByUserIdAndDateBetween(userId, startDate, endDate);

        // 날짜별로 그루핑
        Map<LocalDate, List<ReadingRecord>> groupedByDate = records.stream()
                .collect(Collectors.groupingBy(ReadingRecord::getReadDate));

        // startDate ~ endDate 전체 날짜 생성 (기록 없는 날도 0으로 포함)
        return startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    List<ReadingRecord> dailyRecords = groupedByDate.getOrDefault(date, List.of());
                    int novelCount = (int) dailyRecords.stream()
                            .map(ReadingRecord::getNovelId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .count();
                    int episodeCount = dailyRecords.size(); // 기록 수를 episodeCount로 처리
                    return CalendarDailyResponse.of(date, novelCount, episodeCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyStatResponse getMonthlyStatistics(Long userId, int year, int month) {

        // 미래 달 조회 방지
        YearMonth requested = YearMonth.of(year, month);
        if (requested.isAfter(YearMonth.now())) {
            throw new ServiceErrorException(CalendarExceptionEnum.INVALID_STAT_DATE);
        }

        List<ReadingRecord> records = readingRecordRepository
                .findByUserIdAndYearAndMonth(userId, year, month);

        // 총 읽은 페이지 수
        int totalReadPages = records.stream()
                .mapToInt(r -> r.getReadPage() != null ? r.getReadPage() : 0)
                .sum();

        // 완독한 책 수
        int completedBooks = (int) records.stream()
                .filter(r -> r.getReadingStatus() == ReadingStatus.COMPLETED)
                .count();

        // 일평균 페이지
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
        double dailyAverage = totalReadPages == 0 ? 0.0
                : Math.round((double) totalReadPages / daysInMonth * 10) / 10.0;

        // 가장 많이 읽은 날 (페이지 기준)
        Map<LocalDate, Integer> pagesByDate = records.stream()
                .collect(Collectors.groupingBy(
                        ReadingRecord::getReadDate,
                        Collectors.summingInt(r -> r.getReadPage() != null ? r.getReadPage() : 0)
                ));

        LocalDate mostReadDay = pagesByDate.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        int readingDaysCount = pagesByDate.size();

        return MonthlyStatResponse.of(totalReadPages, completedBooks, dailyAverage, mostReadDay, readingDaysCount);
    }
}
