package com.example.hot6novelcraft.domain.episode.dto.response;

import com.example.hot6novelcraft.domain.episode.entity.Episode;

public record EpisodePurchaseResponse(
    Long episodeId,
    Long novelId,
    String episodeTitle,
    int pointPrice,
    Long remainingBalance
) {
    public static EpisodePurchaseResponse of(Episode episode, Long remainingBalance) {
        return new EpisodePurchaseResponse(
            episode.getId(),
            episode.getNovelId(),
            episode.getTitle(),
            episode.getPointPrice(),
            remainingBalance
        );
    }
}