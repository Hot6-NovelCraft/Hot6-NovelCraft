package com.example.hot6novelcraft.domain.episode.dto.response;

public record EpisodeCreateResponse(Long episodeId) {

    public static EpisodeCreateResponse from(Long episodeId) {
        return new EpisodeCreateResponse(episodeId);
    }
}