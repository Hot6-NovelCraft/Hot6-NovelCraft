package com.example.hot6novelcraft.domain.recommendationAi.client;

import com.example.hot6novelcraft.domain.recommendationAi.dto.NovelSummaryForAi;
import com.example.hot6novelcraft.domain.recommendationAi.dto.UserBehaviorSummary;
import com.example.hot6novelcraft.domain.recommendationAi.prompt.RecommendationPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "RecommendationAiClient")
@Component
@RequiredArgsConstructor
public class RecommendationAiClient {

    private final ChatClient chatClient;
    private final RecommendationPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    // 추천 도메인 전용
    private OpenAiChatOptions buildOptions() {
        return OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .maxTokens(500)         // ID 목록만
                .temperature(0.3)
                .build();
    }

    // 로그인 사용자 개인 추천 호출
    public List<Long> requestPersonalized(
            UserBehaviorSummary behavior
            , Map<String, Integer> genrePreference
            , List<NovelSummaryForAi> candidates
    ) {
        String prompt = promptBuilder.buildPersonalizedPrompt(behavior, genrePreference, candidates);
        return callAndParseIds(prompt);
    }

    // 비로그인 사용자 트렌드 추천 호출 (+ 신규 로그인 사용자 콜드스타트)
    public List<Long> requestTrend(
            List<String> popularTags
            , List<NovelSummaryForAi> candidates
    ) {
        String prompt = promptBuilder.buildTrendPrompt(popularTags, candidates);
        return callAndParseIds(prompt);
    }

    /**
     * ======== AI 호출 ========
     */

    // AI 호출 + ID 파싱 (도메인 전용 옵션 적용해서 호출)
    private List<Long> callAndParseIds(String prompt) {

        try {
            String content = chatClient
                    .prompt(prompt)
                    .options(buildOptions())    // 도메인 전용 옵션 적용
                    .call()
                    .content();

            log.debug("[AI 추천] 응답 원문: {}", content);
            return parseIds(content);

        } catch (Exception e) {
            log.error("[AI 추천] 호출 실패 - 빈 목록 반환", e);

            return Collections.emptyList();
        }
    }

    // AI 응답에서 소설 ID 목록 추출 -> 예시 : List<Long> "[1, 5, 23, 7]" 변환
    private List<Long> parseIds(String content) {

        try {
            // 추천: [1, 5] 처럼 올 경우 배열 부분만 추출
            int startIndex = content.indexOf("[");
            int endIndex = content.lastIndexOf("]");

            if (startIndex == -1 || endIndex == -1) {
                log.error("[AI 추천] 배열 형식 응답 없음: {}", content);
                return Collections.emptyList();
            }

            JsonNode arrayNode = objectMapper.readTree(
                    content.substring(startIndex, endIndex + 1)
            );

            List<Long> novelIds = new ArrayList<>();
            arrayNode.forEach(node -> novelIds.add(node.asLong()));

            log.info("[AI 추천] 파싱 완료: {}개", novelIds.size());

            return novelIds;

        } catch (Exception e) {
            log.error("[AI 추천] 파싱 실패 - content: {}", content, e);

            return Collections.emptyList();
        }
    }
}
