package com.example.hot6novelcraft.domain.novel.dto.response;

import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;

public record NovelListResponse(

        Long novelId,
        String title,
        String genre,
        String tags,
        NovelStatus status,
        String coverImageUrl,
        Long viewCount,
        int bookmarkCount,
        String authorNickname

) {
    public static NovelListResponse of(Long novelId, String title, String genre,
                                       String tags, NovelStatus status, String coverImageUrl,
                                       Long viewCount, int bookmarkCount, String authorNickname) {
        return new NovelListResponse(
                novelId, title, genre, tags, status,
                coverImageUrl, viewCount, bookmarkCount, authorNickname
        );
    }
}