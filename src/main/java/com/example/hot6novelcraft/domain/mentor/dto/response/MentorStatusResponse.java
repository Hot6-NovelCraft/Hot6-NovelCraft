package com.example.hot6novelcraft.domain.mentor.dto.response;

import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;

import java.time.LocalDateTime;

public record MentorStatusResponse(
        Long mentorId,
        MentorStatus status,
        String rejectReason,
        LocalDateTime appliedAt,
        LocalDateTime approvedAt
) {
    public static MentorStatusResponse from(Mentor mentor) {
        return new MentorStatusResponse(
                mentor.getId(),
                mentor.getStatus(),
                mentor.getRejectReason(),
                mentor.getCreatedAt(),
                mentor.getApprovedAt()
        );
    }
}