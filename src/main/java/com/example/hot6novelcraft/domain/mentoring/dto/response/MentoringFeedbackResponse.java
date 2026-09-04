package com.example.hot6novelcraft.domain.mentoring.dto.response;

import com.example.hot6novelcraft.domain.mentor.entity.MentorFeedback;

import java.time.LocalDateTime;

public record MentoringFeedbackResponse(
        Long feedbackId,
        Long mentoringId,
        String title,
        int sessionNumber,
        String content,
        LocalDateTime createdAt
) {
    public static MentoringFeedbackResponse from(MentorFeedback feedback) {
        return new MentoringFeedbackResponse(
                feedback.getId(),
                feedback.getMentorshipId(),
                feedback.getTitle(),
                feedback.getSessionNumber(),
                feedback.getContent(),
                feedback.getCreatedAt()
        );
    }
}