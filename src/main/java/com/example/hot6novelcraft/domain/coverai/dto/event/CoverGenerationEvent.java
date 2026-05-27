package com.example.hot6novelcraft.domain.coverai.dto.event;

public record CoverGenerationEvent(
        String jobId,
        Long novelId,
        Long userId
) {
}