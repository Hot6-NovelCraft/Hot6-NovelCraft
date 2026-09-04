package com.example.hot6novelcraft.domain.reviewai.consumer;

import com.example.hot6novelcraft.domain.reviewai.dto.event.AiReviewMessage;
import com.example.hot6novelcraft.domain.reviewai.service.AiReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * AI 리뷰 Kafka Consumer
 * - Producer가 발행한 메시지를 수신하여 백그라운드에서 OpenAI 호출
 * - 처리 결과는 Redis Job 상태로 저장됨 (성공: COMPLETED, 실패: FAILED)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewConsumer {

    private final AiReviewService aiReviewService;

    /**
     * AI 리뷰 작업 메시지 수신
     * - containerFactory: KafkaConfig의 aiReviewKafkaListenerContainerFactory 빈 사용
     */
    @KafkaListener(
            topics = "${ai-review.kafka.topic}",
            containerFactory = "aiReviewKafkaListenerContainerFactory",
            groupId = "ai-review-service"
    )
    public void consume(AiReviewMessage message) {
        log.info("[AI 리뷰 Consumer 수신] jobId={}, episodeId={}, userId={}",
                message.jobId(), message.episodeId(), message.userId());

        aiReviewService.processReview(message);
    }
}