package com.example.hot6novelcraft.domain.mentoring.dto.response;

import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;

public record MentorWithNickname(
        Long mentorId,
        String nickname,
        CareerLevel careerLevel,
        String mainGenres,      // JSON 문자열
        String specialFields,   // JSON 문자열
        String mentoringStyle,  // JSON 문자열
        String awardsCareer,
        Integer maxMentees
) {
}