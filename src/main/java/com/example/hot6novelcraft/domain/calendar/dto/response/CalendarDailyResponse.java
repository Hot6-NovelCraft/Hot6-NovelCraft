package com.example.hot6novelcraft.domain.calendar.dto.response;

import java.time.LocalDate;

public record CalendarDailyResponse(
        LocalDate date,
        int novelCount,
        int episodeCount
) {
    public static CalendarDailyResponse of(LocalDate date, int novelCount, int episodeCount) {
        return new CalendarDailyResponse(date, novelCount, episodeCount);
    }
}
