package com.example.hot6novelcraft.domain.recommendationAi.service;

import com.example.hot6novelcraft.domain.recommendationAi.dto.UserBehaviorSummary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class UserPreferenceAnalyzer {

    /**
     * ===== 행동 데이터 기반 선호 장르 TOP 3 계산 =====
     * 각 행동별 가중치를 다르게 적용
     * 가중치 기준 :
     * 1. 구매/열람 (recentReadNovels) : 가장 강한 신호 -> 가중치 3
     * 2. 서재 담기 (libraryGenres) : 중간 신호 -> 가중치 2
     * 3. 검색 (recentKeywords) : 약한 신호 -> 가중치 1
     */
    public Map<String, Integer> calculateGenrePreference(UserBehaviorSummary behavior) {

        Map<String, Integer> genreScore = new HashMap<>();

        // 최근 읽은 소설 장르 -> 가중치 3 ("제목 - FANTASY" 형태에서 장르 추출)
        behavior.recentReadNovels().forEach(novelInfo -> {
            String genre = extractGenre(novelInfo);

            if (genre != null) {
                genreScore.merge(genre, 3, Integer::sum);
            }
        });

        // 서재 장르 -> 가중치 2
        behavior.libraryGenres().forEach(genre ->
                genreScore.merge(genre, 2, Integer::sum));

        // 검색어, 인기 태그 교차 분석 -> 가중치 1
        behavior.recentKeywords().forEach(keyword ->
                detectGenreFromKeyword(keyword)
                        .forEach(genre -> genreScore.merge(genre, 1, Integer::sum))
        );

        return genreScore;
    }

    // 선호 장르 "ROMANCE(5점), HEALING(3점)" 형태 문자열로 변환 -> 프롬프트에 직접 삽입
    public String formatPreferenceForPrompt(Map<String, Integer> genreScore) {

        if (genreScore.isEmpty()) {
            return "선호 장르 데이터 없음";
        }

        return genreScore.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey() + "(" + e.getValue() + "점)")
                .collect(Collectors.joining(", "));
    }

    // 선호도 1위 장르 반환 (후보 필터)
    public Optional<String> getTopGenre(Map<String, Integer> genreScore) {
        return genreScore.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    /**
     * 공통 메서드
     */

    // "재벌집 로맨스 - ROMANCE" → "ROMANCE" 추출
    private String extractGenre(String novelInfo) {

        if (novelInfo == null || !novelInfo.contains(" - ")) {
            return null;
        }

        String[] parts = novelInfo.split(" - ");

        return parts.length >= 2 ? parts[parts.length - 1].trim() : null;
    }

    // 검색어에서 장르 유추 (키워드 매핑)
    private List<String> detectGenreFromKeyword(String keyword) {

        if (keyword == null) {
            return Collections.emptyList();
        }
        // key : 키워드(검색어에 포함될 단어), value: 해당 장르
        Map<String, List<String>> KEYWORD_TO_GENRE = Map.ofEntries(
                Map.entry("FANTASY", List.of("이세계", "회귀", "액션", "미스터리", "환생", "빙의", "던전"))
                , Map.entry("ROMANCE_FANTASY", List.of("이세계", "회귀", "로맨스", "하램", "환생"))
                , Map.entry("MODERN", List.of("개그", "힐링", "복수", "성장"))
                , Map.entry("CHIVALROUS", List.of("무공", "아케데미", "액션", "미스터리"))
                , Map.entry("BL", List.of("로맨스", "회귀", "환생", "성인"))
                , Map.entry("SF", List.of("미스터리", "성장", "던전"))
                , Map.entry("HORROR", List.of("성인", "미스터리"))
                , Map.entry("CLASSIC", List.of("힐링", "복수", "성장"))
                , Map.entry("DAILY_LIFE", List.of("힐링", "로맨스", "개그"))
        );

        return KEYWORD_TO_GENRE.entrySet().stream()
                // "이세계"라는 검색어(keyword) 안에 정의된 단어가 포함되어 있는지 검사
                .filter(entry -> entry.getValue().stream().anyMatch(k -> keyword.contains(k)))
                .map(Map.Entry::getKey)           // 검색어에 키워드가 포함되면 장르명으로 반환
                .distinct()
                .collect(Collectors.toList());
    }
}