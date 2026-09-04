package com.example.hot6novelcraft.domain.episode.dto.response;

public record EpisodeDeleteResponse(Long episodeId) {

    public static EpisodeDeleteResponse from(Long episodeId) {
        return new EpisodeDeleteResponse(episodeId);
    }
}