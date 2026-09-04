package com.example.hot6novelcraft.domain.episode.dto.response;

import java.time.LocalDateTime;

public record EpisodeListResponse(

        Long id,
        int episodeNumber,
        String title,
        boolean isFree,
        int pointPrice,
        Long likeCount,
        LocalDateTime publishedAt

) {
}