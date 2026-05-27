package com.example.hot6novelcraft.domain.mentor.dto.request;

import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import jakarta.validation.constraints.*;

import java.util.List;

public record MentorRegisterRequest(

        @NotBlank(message = "멘토 소개는 필수 입력 값입니다")
        @Size(min = 10, max = 500, message = "멘토 소개는 10자 이상 500자 이하로 입력해 주세요")
        String bio,

        @NotNull(message = "주력 장르는 필수 선택 값입니다")
        @Size(min = 1, message = "주력 장르는 1개 이상 선택해 주세요")
        List<String> mainGenres,

        @NotNull(message = "전문 분야는 필수 선택 값입니다")
        @Size(min = 2, message = "전문 분야는 2개 이상 선택해 주세요")
        List<String> specialFields,

        @NotNull(message = "집필 경력은 필수 선택 값입니다")
        CareerLevel careerLevel,

        @Size(max = 500, message = "수상/출간 경력은 500자 이하로 입력해 주세요")
        String awardsCareer,

        @Size(min = 1, message = "멘토링 스타일은 1개 이상 선택해 주세요")
        List<String> mentoringStyles,

        @NotNull(message = "최대 멘티 수는 필수 입력 값입니다")
        @Min(value = 1, message = "최대 멘티 수는 1명 이상이어야 합니다")
        @Max(value = 5, message = "최대 멘티 수는 5명을 초과할 수 없습니다")
        Integer maxMentees,

        @NotNull(message = "신청 즉시 허용 여부는 필수 입력 값입니다")
        Boolean allowInstant,

        @Size(max = 500, message = "환영하는 멘티 유형은 500자 이하로 입력해 주세요")
        String preferredMenteeDesc
) {
}