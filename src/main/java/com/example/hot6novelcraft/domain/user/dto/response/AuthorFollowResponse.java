package com.example.hot6novelcraft.domain.user.dto.response;

public record AuthorFollowResponse(

        boolean isFollowing

) {
    public static AuthorFollowResponse of(boolean isFollowing) {
        return new AuthorFollowResponse(isFollowing);
    }
}