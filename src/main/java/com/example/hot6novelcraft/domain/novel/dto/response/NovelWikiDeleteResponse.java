package com.example.hot6novelcraft.domain.novel.dto.response;

public record NovelWikiDeleteResponse(Long wikiId) {

    public static NovelWikiDeleteResponse from(Long wikiId) {
        return new NovelWikiDeleteResponse(wikiId);
    }
}