package com.example.hot6novelcraft.domain.recommendationAi.dto;

import java.util.List;

/**
 * AI에게 넘길 사용자 행동 요약 데이터
 */
public record UserBehaviorSummary(

        // 최근 구매/읽은 소설 정보
        List<String> recentReadNovels,

        // 서재에 담은 소설들 장르 (관심 장르 파악)
        List<String> libraryGenres,

        // Redis에서 가져온 최근 검색어
        List<String> recentKeywords,

        // 좋아요한 회자 소설 태그
        List<String> likedTags,

        // 플랫폼 전체 인기 태그
        List<String> popularTags
) {
    /**
     * 행동 데이터가 너무 부족하지 않은지 확인
     * 데이터가 거의 없으면 비로그인과 동일하게 트렌드 추천으로 fallback
     */
    public boolean inInsufficientData() {
        return recentReadNovels.isEmpty()
                && libraryGenres.isEmpty()
                && recentKeywords.isEmpty()
                && likedTags.isEmpty()
                && popularTags.isEmpty();
    }

}
