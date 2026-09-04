package com.example.hot6novelcraft.domain.episode.dto.cache;

import com.example.hot6novelcraft.domain.episode.entity.Episode;

public record EpisodeContentCache(
        Long episodeId,
        Long novelId,
        int episodeNumber,
        String title,
        String content,
        Long likeCount,
        boolean isFree,
        int pointPrice
) {
    public static EpisodeContentCache from(Episode episode) {
        return new EpisodeContentCache(
                episode.getId(),
                episode.getNovelId(),
                episode.getEpisodeNumber(),
                episode.getTitle(),
                episode.getContent(),
                episode.getLikeCount(),
                episode.isFree(),
                episode.getPointPrice()
        );
    }
}