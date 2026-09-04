package com.example.hot6novelcraft.domain.novel.dto.response;

public record NovelWikiCreateResponse(Long wikiId) {

    public static NovelWikiCreateResponse from(Long wikiId) {
        return new NovelWikiCreateResponse(wikiId);
    }
}