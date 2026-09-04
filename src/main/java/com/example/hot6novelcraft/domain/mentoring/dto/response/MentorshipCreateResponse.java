package com.example.hot6novelcraft.domain.mentoring.dto.response;

public record MentorshipCreateResponse(
        Long mentorshipId
) {
    public static MentorshipCreateResponse from(Long mentorshipId) {
        return new MentorshipCreateResponse(mentorshipId);
    }
}