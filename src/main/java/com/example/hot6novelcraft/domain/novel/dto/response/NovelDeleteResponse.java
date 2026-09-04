package com.example.hot6novelcraft.domain.novel.dto.response;

public record NovelDeleteResponse(Long novelId) {

    public static NovelDeleteResponse from(Long novelId) {
        return new NovelDeleteResponse(novelId);
    }
}