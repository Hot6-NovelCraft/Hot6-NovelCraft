package com.example.hot6novelcraft.domain.mentor.dto.response;

import java.time.LocalDateTime;

public record MentorUpdateResponse(
        Long mentorId,
        LocalDateTime updatedAt
) {
    public static MentorUpdateResponse from(Long mentorId, LocalDateTime updatedAt) {
        return new MentorUpdateResponse(mentorId, updatedAt);
    }
}