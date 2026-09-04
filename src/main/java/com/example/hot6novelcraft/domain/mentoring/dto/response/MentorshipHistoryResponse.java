package com.example.hot6novelcraft.domain.mentoring.dto.response;

import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;

import java.time.LocalDateTime;

public record MentorshipHistoryResponse(
        Long mentorshipId,
        Long mentorId,
        String mentorNickname,
        MentorshipStatus status,
        LocalDateTime appliedAt,
        String motivation,
        String manuscriptUrl,
        Long currentNovelId,
        boolean hasReview
) {
    // QueryDSL/V1 용 기본 생성자 — hasReview 기본값 false
    public MentorshipHistoryResponse(Long mentorshipId, Long mentorId, String mentorNickname,
                                     MentorshipStatus status, LocalDateTime appliedAt,
                                     String motivation, String manuscriptUrl, Long currentNovelId) {
        this(mentorshipId, mentorId, mentorNickname, status, appliedAt,
                motivation, manuscriptUrl, currentNovelId, false);
    }
}