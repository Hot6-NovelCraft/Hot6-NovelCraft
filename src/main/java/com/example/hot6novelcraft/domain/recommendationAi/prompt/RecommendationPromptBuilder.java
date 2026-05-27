package com.example.hot6novelcraft.domain.recommendationAi.prompt;

import com.example.hot6novelcraft.domain.recommendationAi.dto.NovelSummaryForAi;
import com.example.hot6novelcraft.domain.recommendationAi.dto.UserBehaviorSummary;
import com.example.hot6novelcraft.domain.recommendationAi.service.UserPreferenceAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RecommendationPromptBuilder {

    /**
     * ============ 프롬프트 규칙 ============
     * AI에게 역할 + 데이터 + 출력 형식 명확히 지정
     */

    private final UserPreferenceAnalyzer preferenceAnalyzer;

    // 로그인 사용자 개인 추천 프롬프트
    public String buildPersonalizedPrompt(
            UserBehaviorSummary behavior
            , Map<String, Integer> genrePreference
            , List<NovelSummaryForAi> candidates
    ) {
        // 선호 장르 문자열 계산
        String preferenceText = preferenceAnalyzer.formatPreferenceForPrompt(genrePreference);

        return """
                당신은 까다로운 독자의 취향을 맞추는 웹소설 추천 전문가입니다.
                
                [사용자 행동 데이터]
                - 최근 읽은 소설: %s
                - 서재 장르 목록: %s
                - 최근 검색어: %s
                - 좋아요한 소설 태그: %s
                - 분석된 선호 장르: %s
                
                [추천 후보 소설 목록]
                %s
                
                위 사용자 행동 패턴을 분석해 가장 적합한 소설을 추천해주세요.
                
                [응답 추천 규칙 - 우선순위 순서대로 반드시 준수]
                1. 분석된 선호 장르 1위 소설을 전체 추천의 60%% 이상 포함할 것
                2. 사용자의 최근 검색어와 동일한 장르를 우선 시 해야할 것
                3. 유저가 최근 읽은 장르 Top 25, 전체 Top 25를 섞어서 추천 할 것
                4. 선호 장르 2, 3위 소설로 나머지 채울 것
                5. 유저의 검색어와 서재 내역의 '장르(Genre)'를 최우선으로 분석할 것 
                6. 이미 읽은 소설(최근 읽은 소설 목록)은 절대 제외
                7. 반드시 JSON 배열 형식으로만 응답 (설명 텍스트 절대 금지)
                8. 형식 예시: [1, 5, 23, 7, 12]
                9. 최대 10개, 최소 5개
                10. 이미 읽은 소설은 제외할 것
                11. 다양한 장르 포함 (동일 장르 최대 3개)
                """
                .formatted(
                        formatList(behavior.recentReadNovels())
                        , formatList(behavior.libraryGenres())
                        , formatList(behavior.recentKeywords())
                        , formatList(behavior.likedTags())
                        , preferenceText
                        , formatNovelList(candidates)
                );
    }

    // 비로그인 사용자 트랜드 추천 프롬프트 (+ 신규 로그인 콜드스타트)
    public String buildTrendPrompt(
            List<String> popularTags
            , List<NovelSummaryForAi> candidates
    ) {
        return """
                당신은 웹소설 추천 전문가입니다.
                
                [현재 플랫폼 인기 태그]
                %s
                
                [추천 후보 소설 목록]
                %s
                
                현재 플랫폼 트랜드를 반영해 신규 독자에게 적합한 소설을 추천해주세요
                
                [응답 규칙 - 반드시 준수]
                1. 반드시 JSON 배열 형식으로만 응답 (설명 텍스트 절대 금지)
                2. 형식 예시: [1, 5, 23, 7, 12]
                3. 최대 10개, 최소 5개
                4. 인기 태그와 연관성 높은 소설 우선
                5. 다양한 장르 포함
                """
                .formatted(
                        formatList(popularTags)
                        , formatNovelList(candidates)
                );
    }

    // 공통 메소드
    private String formatNovelList(List<NovelSummaryForAi> novels) {
        return novels.stream()
                .map(n -> "ID:%d | 제목:%s | 장르:%s | 태그:%s"
                        .formatted(n.id(), n.title(), n.genre(), n.tags()))
                .collect(Collectors.joining("\n"));
    }

    private String formatList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "없음";
        }
        return String.join(", ", list);
    }
}