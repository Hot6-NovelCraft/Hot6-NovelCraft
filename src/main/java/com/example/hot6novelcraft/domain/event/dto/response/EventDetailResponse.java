package com.example.hot6novelcraft.domain.event.dto.response;

import com.example.hot6novelcraft.domain.event.entity.Event;
import com.example.hot6novelcraft.domain.event.entity.enums.EventStatus;

import java.time.LocalDateTime;

public record EventDetailResponse(
        Long id,
        String title,
        String description,
        Long rewardPoints,
        Long maxParticipants,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        EventStatus status
) {
    public static EventDetailResponse from(Event event) {
        EventStatus status;
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getStartedAt())) {
            status = EventStatus.UPCOMING;
        } else if (now.isAfter(event.getEndedAt())) {
            status = EventStatus.ENDED;
        } else {
            status = EventStatus.ONGOING;
        }

        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getRewardPoints(),
                event.getMaxParticipants(),
                event.getStartedAt(),
                event.getEndedAt(),
                status
        );
    }
}
