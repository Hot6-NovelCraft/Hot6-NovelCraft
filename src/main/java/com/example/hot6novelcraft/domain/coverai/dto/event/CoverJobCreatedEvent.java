package com.example.hot6novelcraft.domain.coverai.dto.event;

public record CoverJobCreatedEvent(
        String jobId,
        Long novelId,
        Long userId
) {
}
