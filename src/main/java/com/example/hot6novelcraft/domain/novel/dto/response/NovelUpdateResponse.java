package com.example.hot6novelcraft.domain.novel.dto.response;

public record NovelUpdateResponse(Long novelId) {

    public static NovelUpdateResponse from(Long novelId) {
        return new NovelUpdateResponse(novelId);
    }
}