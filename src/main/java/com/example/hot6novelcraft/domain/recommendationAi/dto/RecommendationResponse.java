package com.example.hot6novelcraft.domain.recommendationAi.dto;

import com.example.hot6novelcraft.domain.novel.dto.response.NovelListResponse;

import java.util.List;

public record RecommendationResponse(
        List<NovelListResponse> novels
        , String recommendationType
) {
}