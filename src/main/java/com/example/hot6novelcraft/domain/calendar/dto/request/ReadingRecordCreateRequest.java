package com.example.hot6novelcraft.domain.calendar.dto.request;

import com.example.hot6novelcraft.domain.calendar.entity.enums.ReadingStatus;
import com.example.hot6novelcraft.domain.calendar.entity.enums.RecordSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record ReadingRecordCreateRequest(

        Long novelId,// PLATFORM일 경우 필수, EXTERNAL이면 null

        @NotNull(message = "출처를 선택해주세요")
        RecordSource source,

        @NotNull(message = "독서 날짜를 입력해주세요")
        @PastOrPresent(message = "독서 날짜는 오늘 이전이어야 합니다")
        LocalDate readDate,

        String title,// EXTERNAL일 경우 필수
        String authorName,// EXTERNAL일 경우 필수
        String note,// 선택

        @Min(value = 0, message = "읽은 페이지 수는 음수일 수 없습니다")
        Integer readPage,

        @Min(value = 1, message = "전체 페이지 수는 1 이상이어야 합니다")
        Integer totalPage,

        @NotNull(message = "독서 상태를 선택해주세요")
        ReadingStatus readingStatus
) {
}
