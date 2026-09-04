package com.example.hot6novelcraft.domain.event.dto.response;

import com.example.hot6novelcraft.domain.event.entity.EventParticipant;

import java.time.LocalDateTime;

public record EventParticipateResponse(
        Long eventId,
        Long userId,
        Long rewardPoints,
        LocalDateTime joinedAt
) {
    public static EventParticipateResponse from(EventParticipant participant, Long rewardPoints) {
        return new EventParticipateResponse(
                participant.getEventId(),
                participant.getUserId(),
                rewardPoints,
                participant.getJoinedAt()
        );
    }
}