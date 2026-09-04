package com.example.hot6novelcraft.domain.novel.dto.response;

public record NovelCreateResponse(Long novelId) {

    // 정적 팩토리
    public static NovelCreateResponse from(Long novelId) {
        return new NovelCreateResponse(novelId);
    }
}