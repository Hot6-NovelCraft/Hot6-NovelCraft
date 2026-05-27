package com.example.hot6novelcraft.domain.mentoring.dto.response;

import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;

import java.time.LocalDateTime;

public record MentoringReceivedResponse(
        Long mentoringId,
        Long menteeId,
        String menteeName,
        String title,
        LocalDateTime appliedAt,
        MentorshipStatus status
) {
    public static MentoringReceivedResponse of(Mentorship mentorship, String menteeName, String title) {
        return new MentoringReceivedResponse(
                mentorship.getId(),
                mentorship.getMenteeId(),
                menteeName,
                title,
                mentorship.getCreatedAt(),
                mentorship.getStatus()
        );
    }
}