package com.example.hot6novelcraft.domain.episode.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EpisodeCreateRequest(

        @NotNull(message = "회차 번호는 필수입니다.")
        @Min(value = 1, message = "회차 번호는 1 이상이어야 합니다.")
        Integer episodeNumber,

        @NotBlank(message = "회차 제목은 필수입니다.")
        @Size(max = 20, message = "회차 제목은 최대 20자까지 입력 가능합니다.")
        String title,

        @Size(max = 5000, message = "회차 본문은 최대 5000자까지 입력 가능합니다.")
        String content

) {
}