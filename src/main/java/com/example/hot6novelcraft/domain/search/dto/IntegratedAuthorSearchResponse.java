package com.example.hot6novelcraft.domain.search.dto;

import java.util.List;

// 작가 검색 통합 결과
public record IntegratedAuthorSearchResponse(

        // 닉네임 유사 작가들
        List<AuthorSearchResponse> matchingAuthors

        // 제목에 키워드 포함된 소설 (제목+작가만)
        , List<NovelSimpleResponse> matchingNovels
) {}
