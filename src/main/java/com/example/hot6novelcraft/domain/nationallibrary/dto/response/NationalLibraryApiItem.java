package com.example.hot6novelcraft.domain.nationallibrary.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NationalLibraryApiItem(
        @JsonProperty("isbn")
        String isbn,

        @JsonProperty("titleInfo")
        String title,

        @JsonProperty("authorInfo")
        String author,

        @JsonProperty("pubInfo")
        String publisher,

        @JsonProperty("pubYearInfo")
        String publishYear,

        @JsonProperty("detailLink")
        String titleUrl,

        @JsonProperty("imageUrl")
        String imageUrl
) {
}