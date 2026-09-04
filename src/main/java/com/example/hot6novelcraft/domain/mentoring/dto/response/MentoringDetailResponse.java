package com.example.hot6novelcraft.domain.mentoring.dto.response;

import com.example.hot6novelcraft.domain.mentor.entity.MentorFeedback;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;

import java.time.LocalDateTime;
import java.util.List;

public record MentoringDetailResponse(
        Long mentoringId,
        String novelTitle,          // title → novelTitle 로 교체
        String mentorName,
        String menteeName,
        MentorshipStatus status,
        LocalDateTime startDate,
        int totalSessions,
        List<FeedbackInfo> feedbacks
) {
    public record FeedbackInfo(
            Long feedbackId,
            String title,           // 추가
            int sessionNumber,      // 추가
            String content,
            LocalDateTime createdAt
    ) {
        public static FeedbackInfo from(MentorFeedback feedback) {
            return new FeedbackInfo(
                    feedback.getId(),
                    feedback.getTitle(),
                    feedback.getSessionNumber(),
                    feedback.getContent(),
                    feedback.getCreatedAt()
            );
        }
    }

    public static MentoringDetailResponse of(Mentorship mentorship, String mentorName,
                                             String menteeName, String novelTitle,
                                             List<FeedbackInfo> feedbacks) {
        return new MentoringDetailResponse(
                mentorship.getId(),
                novelTitle,
                mentorName,
                menteeName,
                mentorship.getStatus(),
                mentorship.getAcceptedAt(),
                mentorship.getTotalSessions(),
                feedbacks
        );
    }
}