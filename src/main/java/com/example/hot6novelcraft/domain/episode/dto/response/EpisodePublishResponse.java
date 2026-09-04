package com.example.hot6novelcraft.domain.episode.dto.response;

public record EpisodePublishResponse(Long episodeId) {

    public static EpisodePublishResponse from(Long episodeId) {
        return new EpisodePublishResponse(episodeId);
    }
}