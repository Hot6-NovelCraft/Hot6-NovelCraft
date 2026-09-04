package com.example.hot6novelcraft.domain.novel.dto.response;

import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;

import java.time.LocalDateTime;

public record AuthorNovelListResponse(

        Long id,
        String title,
        String genre,
        NovelStatus status,
        String coverImageUrl,
        long episodeCount,
        Long viewCount,
        int bookmarkCount,
        LocalDateTime updatedAt

) {
}