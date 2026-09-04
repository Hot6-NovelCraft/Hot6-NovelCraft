package com.example.hot6novelcraft.domain.novel.dto.response;

public record NovelBookmarkResponse(

        boolean isBookmarked

) {
    public static NovelBookmarkResponse of(boolean isBookmarked) {
        return new NovelBookmarkResponse(isBookmarked);
    }
}