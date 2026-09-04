package com.example.hot6novelcraft.domain.episode.dto.response;

public record EpisodeCommentCreateResponse(

        Long commentId

) {
    public static EpisodeCommentCreateResponse from(Long commentId) {
        return new EpisodeCommentCreateResponse(commentId);
    }
}