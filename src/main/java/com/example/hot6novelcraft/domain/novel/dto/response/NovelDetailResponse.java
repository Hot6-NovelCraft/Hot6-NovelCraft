package com.example.hot6novelcraft.domain.novel.dto.response;

import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;

import java.time.LocalDateTime;

public record NovelDetailResponse(

        Long novelId,
        String title,
        String description,
        String genre,
        String tags,
        NovelStatus status,
        String coverImageUrl,
        Long viewCount,
        int bookmarkCount,
        String authorNickname,
        Long authorId,
        LocalDateTime createdAt

) {
    public static NovelDetailResponse of(Novel novel, String authorNickname) {
        return new NovelDetailResponse(
                novel.getId(),
                novel.getTitle(),
                novel.getDescription(),
                novel.getGenre(),
                novel.getTags(),
                novel.getStatus(),
                novel.getCoverImageUrl(),
                novel.getViewCount(),
                novel.getBookmarkCount(),
                authorNickname,
                novel.getAuthorId(),
                novel.getCreatedAt()
        );
    }

    // Redis 조회수 합산용
    public NovelDetailResponse withViewCount(Long newViewCount) {
        return new NovelDetailResponse(
                novelId, title, description, genre, tags,
                status, coverImageUrl, newViewCount,
                bookmarkCount, authorNickname, authorId, createdAt
        );
    }
}
