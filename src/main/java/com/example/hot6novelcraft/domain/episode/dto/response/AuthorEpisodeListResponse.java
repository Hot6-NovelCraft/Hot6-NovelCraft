package com.example.hot6novelcraft.domain.episode.dto.response;

import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;

import java.time.LocalDateTime;

public record AuthorEpisodeListResponse(

        Long id,
        int episodeNumber,
        String title,
        EpisodeStatus status,
        boolean isFree,
        int pointPrice,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt

) {
}