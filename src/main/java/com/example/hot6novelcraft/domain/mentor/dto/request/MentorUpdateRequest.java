package com.example.hot6novelcraft.domain.mentor.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MentorUpdateRequest(

        @Size(min = 10, max = 500, message = "멘토 소개는 10자 이상 500자 이하로 입력해 주세요")
        String introduction,

        List<String> mainGenres,

        List<String> specialFields,

        @Size(max = 500, message = "경력 사항은 500자 이하로 입력해 주세요")
        String careerHistory,

        List<String> mentoringStyles,

        @Min(value = 1, message = "최대 멘티 수는 1명 이상이어야 합니다")
        @Max(value = 5, message = "최대 멘티 수는 5명을 초과할 수 없습니다")
        Integer maxMentees,

        Boolean allowInstant,

        @Size(max = 500, message = "환영하는 멘티 유형은 500자 이하로 입력해 주세요")
        String preferredMenteeDesc
) {
}