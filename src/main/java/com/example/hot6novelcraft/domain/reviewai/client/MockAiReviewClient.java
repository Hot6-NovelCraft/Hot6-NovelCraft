package com.example.hot6novelcraft.domain.reviewai.client;

import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// Mock AI 리뷰 클라이언트 (부하테스트용)
@Slf4j
@Component
@Profile("test")
public class MockAiReviewClient implements AiReviewClient {

    // 실제 OpenAI 응답 시간 시뮬레이션 (11~18초 랜덤)
    private static final long MOCK_DELAY_MIN_MS = 11_000L;  // 11초
    private static final long MOCK_DELAY_MAX_MS = 18_000L;  // 18초

    @Override
    public AiReviewResponse generate(Long episodeId, String title, String content) {

        // 11~18초 사이 랜덤 딜레이
        long delay = ThreadLocalRandom.current().nextLong(MOCK_DELAY_MIN_MS, MOCK_DELAY_MAX_MS + 1);

        log.info("[Mock AI 리뷰 호출] episodeId={}, delay={}ms", episodeId, delay);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mock 처리 중 인터럽트", e);
        }

        // 가짜 댓글 데이터 반환
        return new AiReviewResponse(
                episodeId,
                List.of(
                        new AiReviewResponse.AiCommentResponse(
                                "달빛독자", "와 이번 소설 정말 재밌어요!! 😭", 4.5),
                        new AiReviewResponse.AiCommentResponse(
                                "소설덕후777", "다음화 빨리 보고싶다 ㄷㄷ", 4.0),
                        new AiReviewResponse.AiCommentResponse(
                                "정주행중", "주인공 각성 장면 진짜 소름이었어요. 작가님 감정선 잘 살리시는듯", 4.5),
                        new AiReviewResponse.AiCommentResponse(
                                "결말이뭐야", "이번화 미쳤다", 5.0),
                        new AiReviewResponse.AiCommentResponse(
                                "새벽감성러", "복선 미쳤다 진짜 ㄷㄷㄷ", 4.5),
                        new AiReviewResponse.AiCommentResponse(
                                "띵작헌터", "이번화에서 보여준 캐릭터의 변화가 너무 인상적이었어요. 작가님의 섬세한 감정 묘사 덕분에 몰입도가 정말 높았습니다", 5.0)
                )
        );
    }
}