package com.example.hot6novelcraft.domain.calendar.dto.response;

import com.example.hot6novelcraft.domain.calendar.entity.ReadingRecord;
import com.example.hot6novelcraft.domain.calendar.entity.enums.RecordSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReadingRecordCreateResponse(
        Long id,
        Long novelId,
        String title,
        RecordSource source,
        LocalDate readDate,
        LocalDateTime createdAt
) {
    public static ReadingRecordCreateResponse from(ReadingRecord record) {
        return new ReadingRecordCreateResponse(
                record.getId(),
                record.getNovelId(),
                record.getTitle(),
                record.getSource(),
                record.getReadDate(),
                record.getCreatedAt()
        );
    }
}
