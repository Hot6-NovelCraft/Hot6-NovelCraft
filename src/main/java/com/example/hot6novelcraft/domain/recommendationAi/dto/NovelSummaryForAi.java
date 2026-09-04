package com.example.hot6novelcraft.domain.recommendationAi.dto;

// AI에게 넘길 소설 요약 정보 (무거운 정보들)
public record NovelSummaryForAi(

        Long id
        , String coverImageUrl
        , String title
        , String genre
        , String tags
        , Long authorId // 작가 닉네임
) {
}