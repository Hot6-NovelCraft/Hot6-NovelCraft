package com.example.hot6novelcraft.domain.episode.dto.response;

import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;

public record AuthorEpisodeDetailResponse(
        Long episodeId,
        int episodeNumber,
        String title,
        String content,
        EpisodeStatus status,
        boolean isFree,
        int pointPrice
) {
    public static AuthorEpisodeDetailResponse from(Episode episode) {
        return new AuthorEpisodeDetailResponse(
                episode.getId(),
                episode.getEpisodeNumber(),
                episode.getTitle(),
                episode.getContent(),
                episode.getStatus(),
                episode.isFree(),
                episode.getPointPrice()
        );
    }
}
