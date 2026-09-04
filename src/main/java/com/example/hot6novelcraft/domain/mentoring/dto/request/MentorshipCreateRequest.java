package com.example.hot6novelcraft.domain.mentoring.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MentorshipCreateRequest(

        @NotNull
        Long mentorId,

        @Size(max = 500, message = "신청 동기는 최대 500자입니다.")
        String motivation,

        Long currentNovelId,  // 소설을 등록은 선택!

        String manuscriptUrl  // 선택 (업로드 API로 먼저 받은 S3 URL)
) {
}
