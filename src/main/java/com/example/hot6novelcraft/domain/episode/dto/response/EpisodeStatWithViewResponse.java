package com.example.hot6novelcraft.domain.episode.dto.response;

public record EpisodeStatWithViewResponse(
        Long episodeId,
        int episodeNumber,
        String title,
        long todayViewCount, // Redis
        long likeCount,
        long commentCount,
        long purchaseCount
) {
}
