package com.example.hot6novelcraft.domain.novel.dto.response;

import com.example.hot6novelcraft.domain.novel.entity.NovelWiki;
import com.example.hot6novelcraft.domain.novel.entity.enums.WikiCategory;

public record NovelWikiResponse(

        Long wikiId,
        Long novelId,
        WikiCategory category,
        String title,
        String content,
        String createdAt   // LocalDateTime → String

) {
    public static NovelWikiResponse from(NovelWiki wiki) {
        return new NovelWikiResponse(
                wiki.getId(),
                wiki.getNovelId(),
                wiki.getCategory(),
                wiki.getTitle(),
                wiki.getContent(),
                wiki.getCreatedAt() != null ? wiki.getCreatedAt().toString() : null
        );
    }
}