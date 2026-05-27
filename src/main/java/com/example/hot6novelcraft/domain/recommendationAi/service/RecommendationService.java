package com.example.hot6novelcraft.domain.recommendationAi.service;

import com.example.hot6novelcraft.domain.recommendationAi.client.RecommendationAiClient;
import com.example.hot6novelcraft.domain.recommendationAi.dto.NovelSummaryForAi;
import com.example.hot6novelcraft.domain.recommendationAi.dto.UserBehaviorSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j(topic = "AiRecommendationService")
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationAiClient recommendationAiClient;

    // 로그인 사용자 개인 맞춤 추천
    public List<Long> getPersonalizedRecommendations(
            UserBehaviorSummary behavior
            , Map<String, Integer> genrePreference
            , List<NovelSummaryForAi> candidates
    ) {
        return recommendationAiClient.requestPersonalized(behavior, genrePreference, candidates);
    }

    // 비로그인 및 신규 사용자 콜드스타트 - 트랜드 추천
    public List<Long> getTrendRecommendations(
            List<String> popularTags
            , List<NovelSummaryForAi> candidates
    ) {
        return recommendationAiClient.requestTrend(popularTags, candidates);
    }
}
