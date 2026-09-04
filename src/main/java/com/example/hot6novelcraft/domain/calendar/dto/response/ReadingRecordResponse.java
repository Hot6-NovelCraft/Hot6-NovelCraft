package com.example.hot6novelcraft.domain.calendar.dto.response;

import com.example.hot6novelcraft.domain.calendar.entity.ReadingRecord;
import com.example.hot6novelcraft.domain.calendar.entity.enums.RecordSource;

import java.time.LocalDate;

public record ReadingRecordResponse(
        Long id,
        Long novelId,
        String title,
        String authorName,
        RecordSource source,
        LocalDate readDate,
        String note
) {
    public static ReadingRecordResponse from(ReadingRecord record) {
        return new ReadingRecordResponse(
                record.getId(),
                record.getNovelId(),
                record.getTitle(),
                record.getAuthorName(),
                record.getSource(),
                record.getReadDate(),
                record.getNote()
        );
    }
}
