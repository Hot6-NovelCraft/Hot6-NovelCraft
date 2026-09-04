package com.example.hot6novelcraft.domain.episode.dto.response;

public record EpisodeUpdateResponse(Long episodeId) {

    public static EpisodeUpdateResponse from(Long episodeId) {
        return new EpisodeUpdateResponse(episodeId);
    }
}