package com.example.hot6novelcraft.domain.event.dto.response;

import com.example.hot6novelcraft.domain.event.entity.EventParticipant;

import java.time.LocalDateTime;

public record EventParticipantResponse(
        Long participantId,
        Long userId,
        LocalDateTime joinedAt
) {
    public static EventParticipantResponse from(EventParticipant participant) {
        return new EventParticipantResponse(
                participant.getId(),
                participant.getUserId(),
                participant.getJoinedAt()
        );
    }
}