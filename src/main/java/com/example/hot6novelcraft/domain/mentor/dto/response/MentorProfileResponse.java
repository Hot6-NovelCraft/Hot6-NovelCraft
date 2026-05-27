package com.example.hot6novelcraft.domain.mentor.dto.response;

import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

public record MentorProfileResponse(
        Long mentorId,
        String introduction,
        List<String> mainGenres,
        List<String> specialFields,
        CareerLevel careerLevel,
        String careerHistory,
        List<String> mentoringStyles,
        Integer maxMentees,
        Boolean allowInstant,
        String preferredMenteeDesc,
        MentorStatus status
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static MentorProfileResponse from(Mentor mentor) {
        return new MentorProfileResponse(
                mentor.getId(),
                mentor.getBio(),
                parseJson(mentor.getMainGenres()),
                parseJson(mentor.getSpecialFields()),
                mentor.getCareerLevel(),
                mentor.getAwardsCareer(),
                parseJson(mentor.getMentoringStyle()),
                mentor.getMaxMentees(),
                mentor.getAllowInstant(),
                mentor.getPreferredMenteeDesc(),
                mentor.getStatus()
        );
    }

    private static List<String> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}