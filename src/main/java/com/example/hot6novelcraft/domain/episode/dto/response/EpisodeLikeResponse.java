package com.example.hot6novelcraft.domain.episode.dto.response;

public record EpisodeLikeResponse(

        boolean isLiked,
        long likeCount

) {
    public static EpisodeLikeResponse of(boolean isLiked, long likeCount) {
        return new EpisodeLikeResponse(isLiked, likeCount);
    }
}