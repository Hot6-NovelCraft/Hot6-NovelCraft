package com.example.hot6novelcraft.domain.event.dto.response;

import com.example.hot6novelcraft.domain.event.entity.Event;
import com.example.hot6novelcraft.domain.event.entity.enums.EventStatus;

import java.time.LocalDateTime;

public record EventSummaryResponse(
        Long id,
        String title,
        Long rewardPoints,
        Long maxParticipants,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        EventStatus status
) {
    public static EventSummaryResponse from(Event event, EventStatus status) {
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                event.getRewardPoints(),
                event.getMaxParticipants(),
                event.getStartedAt(),
                event.getEndedAt(),
                status
        );
    }
}
