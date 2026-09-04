package com.example.hot6novelcraft.domain.mentoring.dto.response;

import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;

import java.util.List;

public record MentorshipDetailResponse(
        Long mentorId,
        String nickname,
        CareerLevel careerLevel,
        List<String> mainGenres,
        List<String> specialFields,
        List<String> mentoringStyle,
        String awardsCareer,
        String bio,
        Integer maxMentees
) {
    public static MentorshipDetailResponse of(
            Long mentorId,
            String nickname,
            CareerLevel careerLevel,
            List<String> mainGenres,
            List<String> specialFields,
            List<String> mentoringStyle,
            String awardsCareer,
            String bio,
            Integer maxMentees
    ) {
        return new MentorshipDetailResponse(
                mentorId, nickname, careerLevel,
                mainGenres, specialFields, mentoringStyle,
                awardsCareer, bio, maxMentees
        );
    }
}