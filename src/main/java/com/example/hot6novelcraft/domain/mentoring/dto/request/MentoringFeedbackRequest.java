package com.example.hot6novelcraft.domain.mentoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MentoringFeedbackRequest(
        @NotBlank(message = "피드백 제목을 입력해 주세요")
        @Size(max = 200, message = "피드백 제목은 200자 이하로 입력해 주세요")
        String title,

        @NotBlank(message = "피드백 내용을 입력해 주세요")
        String content
) {
}