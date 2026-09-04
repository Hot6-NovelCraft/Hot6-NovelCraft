package com.example.hot6novelcraft.domain.search.dto;

import java.util.List;

public record AuthorSearchResponse(
        Long authorId
        , String nickname
        , String bio
        , List<NovelSimple> topNovels
) {
    public record NovelSimple(Long id, String title, String coverImageUrl) {}
}
