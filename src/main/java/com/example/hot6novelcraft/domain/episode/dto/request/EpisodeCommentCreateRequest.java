package com.example.hot6novelcraft.domain.episode.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EpisodeCommentCreateRequest(

        @NotBlank(message = "댓글 내용은 필수입니다.")
        @Size(max = 500, message = "댓글은 최대 500자까지 작성 가능합니다.")
        String content

) {
}