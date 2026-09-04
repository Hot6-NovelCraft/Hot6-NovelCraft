package com.example.hot6novelcraft.domain.mentoring.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MentorshipReviewRequest(

        @NotNull(message = "별점은 필수입니다.")
        @Min(value = 1, message = "별점은 최소 1점입니다.")
        @Max(value = 5, message = "별점은 최대 5점입니다.")
        Integer rating,

        @Size(max = 100, message = "평가 내용은 최대 100자입니다.")
        String content
) {
}