package com.example.hot6novelcraft.domain.episode.dto.request;

import jakarta.validation.constraints.Size;

public record EpisodeUpdateRequest(

        @Size(max = 20, message = "회차 제목은 최대 20자까지 입력 가능합니다.")
        String title,

        @Size(max = 5000, message = "회차 본문은 최대 5000자까지 입력 가능합니다.")
        String content

) {
}