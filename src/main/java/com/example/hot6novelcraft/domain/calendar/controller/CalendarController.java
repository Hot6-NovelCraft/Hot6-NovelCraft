package com.example.hot6novelcraft.domain.calendar.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.calendar.dto.request.ReadingRecordCreateRequest;
import com.example.hot6novelcraft.domain.calendar.dto.response.CalendarDailyResponse;
import com.example.hot6novelcraft.domain.calendar.dto.response.MonthlyStatResponse;
import com.example.hot6novelcraft.domain.calendar.dto.response.ReadingRecordCreateResponse;
import com.example.hot6novelcraft.domain.calendar.dto.response.ReadingRecordResponse;
import com.example.hot6novelcraft.domain.calendar.service.CalendarService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/calendars")
@RequiredArgsConstructor
@Validated
public class CalendarController {

    private final CalendarService calendarService;

    @PostMapping("/me/records")
    public ResponseEntity<BaseResponse<ReadingRecordCreateResponse>> createReadingRecord(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody ReadingRecordCreateRequest request
    ) {
        Long userId = userDetails.getUser().getId();
        ReadingRecordCreateResponse response = calendarService.createReadingRecord(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.success(HttpStatus.CREATED.name(), "독서 기록이 성공적으로 등록되었습니다", response));
    }

    @GetMapping("/me/records")
    public ResponseEntity<BaseResponse<PageResponse<ReadingRecordResponse>>> getReadingRecords(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long novelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long userId = userDetails.getUser().getId();
        PageResponse<ReadingRecordResponse> response = calendarService.getReadingRecords(
                userId, date, novelId, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "독서 기록 조회 성공", response));
    }

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<List<CalendarDailyResponse>>> getCalendarRecords(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        Long userId = userDetails.getUser().getId();
        List<CalendarDailyResponse> response = calendarService.getCalendarRecords(userId, startDate, endDate);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "독서 캘린더 데이터 조회가 완료되었습니다", response)
        );
    }

    @GetMapping("/me/statistics")
    public ResponseEntity<BaseResponse<MonthlyStatResponse>> getMonthlyStatistics(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam @Min(2000) int year,               // 연도 최소값 제한
            @RequestParam @Min(1) @Max(12) int month         // 1~12월 범위 제한
    ) {
        Long userId = userDetails.getUser().getId();
        MonthlyStatResponse response = calendarService.getMonthlyStatistics(userId, year, month);
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "월간 통계 조회 성공", response)
        );
    }
}
