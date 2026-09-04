package com.example.hot6novelcraft.domain.search.dto;

public record NovelSearchResponse(
        Long novelId
        , String coverImageUrl
        , String title
        , String authorName
        , String genre
) {
}
