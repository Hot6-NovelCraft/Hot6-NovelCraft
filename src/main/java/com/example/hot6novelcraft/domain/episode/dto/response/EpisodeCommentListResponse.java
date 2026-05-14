package com.example.hot6novelcraft.domain.episode.dto.response;

import java.time.LocalDateTime;

public record EpisodeCommentListResponse(

        Long id,
        Long userId,
        String userNickname,
        String content,
        LocalDateTime createdAt

) {
}