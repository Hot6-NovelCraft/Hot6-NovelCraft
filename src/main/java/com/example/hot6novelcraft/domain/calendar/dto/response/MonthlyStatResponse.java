package com.example.hot6novelcraft.domain.calendar.dto.response;

import java.time.LocalDate;

public record MonthlyStatResponse(
        int totalReadPages,       // 총 읽은 페이지 수
        int completedBooks,       // 완독한 책 수
        double dailyAverage,      // 일평균 페이지 수
        LocalDate mostReadDay,    // 가장 많이 읽은 날
        int readingDaysCount      // 기록이 있는 날 수
) {
    public static MonthlyStatResponse of(
            int totalReadPages,
            int completedBooks,
            double dailyAverage,
            LocalDate mostReadDay,
            int readingDaysCount
    ) {
        return new MonthlyStatResponse(
                totalReadPages,
                completedBooks,
                dailyAverage,
                mostReadDay,
                readingDaysCount
        );
    }
}
