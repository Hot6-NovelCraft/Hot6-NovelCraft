package com.example.hot6novelcraft.domain.mentor.dto.response;

import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;

import java.time.LocalDateTime;

public record MentorRegisterResponse(
        Long applicationId,
        MentorStatus status,
        LocalDateTime appliedAt
) {
    public static MentorRegisterResponse from(Mentor mentor) {
        return new MentorRegisterResponse(
                mentor.getId(),
                mentor.getStatus(),
                mentor.getCreatedAt()
        );
    }
}