package com.example.hot6novelcraft.domain.episode.dto.response;

public record EpisodeStatResponse(
        Long episodeId,
        int episodeNumber,
        String title,
        long likeCount,      // 회차 좋아요 count
        long commentCount,   // 댓글 count
        long purchaseCount   // point_histories 회차구매 count
) {
}