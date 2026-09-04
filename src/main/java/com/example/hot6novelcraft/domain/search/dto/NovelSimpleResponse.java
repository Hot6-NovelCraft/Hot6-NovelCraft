package com.example.hot6novelcraft.domain.search.dto;

// 작가 심플 검색
public record NovelSimpleResponse(
        Long novelId
        , String title
        , String authorNickname
        , String tags
) {
    /** 작가 통합 검색 - 소설 제목, 작가명 (novelId 없음) */
    public NovelSimpleResponse(String title, String authorNickname) {
        this(null, title, authorNickname, null);
    }
}
