package com.example.hot6novelcraft.domain.mentoring.dto.response;

import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;

import java.util.List;

public record MentorshipListResponse(
        Long mentorId,
        String nickname,
        CareerLevel careerLevel,
        List<String> mainGenres,
        List<String> specialFields,
        List<String> mentoringStyle,
        String awardsCareer,
        Integer maxMentees
) {
    public static MentorshipListResponse of(
            Long mentorId,
            String nickname,
            CareerLevel careerLevel,
            List<String> mainGenres,
            List<String> specialFields,
            List<String> mentoringStyle,
            String awardsCareer,
            Integer maxMentees
    ) {
        return new MentorshipListResponse(
                mentorId, nickname, careerLevel,
                mainGenres, specialFields, mentoringStyle,
                awardsCareer, maxMentees
        );
    }


}