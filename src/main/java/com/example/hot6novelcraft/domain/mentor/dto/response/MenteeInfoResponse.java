package com.example.hot6novelcraft.domain.mentor.dto.response;

import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;

import java.time.LocalDateTime;

public record MenteeInfoResponse(
        Long mentoringId,
        Long menteeId,
        String menteeName,
        String novelTitle,
        int totalSessions,
        int manuscriptDownloadCount,
        MentorshipStatus status,
        LocalDateTime acceptedAt,
        LocalDateTime lastFeedbackAt
) {
    public static MenteeInfoResponse of(Mentorship mentorship, String menteeName,
                                        String novelTitle, LocalDateTime lastFeedbackAt) {
        return new MenteeInfoResponse(
                mentorship.getId(),
                mentorship.getMenteeId(),
                menteeName,
                novelTitle,
                mentorship.getTotalSessions(),
                mentorship.getManuscriptDownloadCount(),
                mentorship.getStatus(),
                mentorship.getAcceptedAt(),
                lastFeedbackAt
        );
    }
}