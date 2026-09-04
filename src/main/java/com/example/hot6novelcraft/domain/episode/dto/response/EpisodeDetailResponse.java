package com.example.hot6novelcraft.domain.episode.dto.response;

import com.example.hot6novelcraft.domain.episode.entity.Episode;

public record EpisodeDetailResponse(

        Long episodeId,
        int episodeNumber,
        String title,
        String content,
        Long likeCount,
        boolean isFree,
        int pointPrice

) {
    public static EpisodeDetailResponse from(Episode episode) {
        return new EpisodeDetailResponse(
                episode.getId(),
                episode.getEpisodeNumber(),
                episode.getTitle(),
                episode.getContent(),
                episode.getLikeCount(),
                episode.isFree(),
                episode.getPointPrice()
        );
    }
}