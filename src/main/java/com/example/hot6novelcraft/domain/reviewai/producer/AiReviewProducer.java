package com.example.hot6novelcraft.domain.reviewai.producer;

import com.example.hot6novelcraft.domain.reviewai.dto.event.AiReviewMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class AiReviewProducer {

    private final KafkaTemplate<String, AiReviewMessage> kafkaTemplate;
    private final String topic;

    public AiReviewProducer(
            KafkaTemplate<String, AiReviewMessage> aiReviewKafkaTemplate,
            @Value("${ai-review.kafka.topic}") String topic
    ) {
        this.kafkaTemplate = aiReviewKafkaTemplate;
        this.topic = topic;
    }

    // AI 리뷰 작업 메시지 발행
    public CompletableFuture<SendResult<String, AiReviewMessage>> send(AiReviewMessage message) {
        log.info("[AI 리뷰 Kafka 발행] topic={}, jobId={}, episodeId={}",
                topic, message.jobId(), message.episodeId());

        CompletableFuture<SendResult<String, AiReviewMessage>> future =
                kafkaTemplate.send(topic, message.jobId(), message);

        future
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[AI 리뷰 Kafka 발행 실패] jobId={}", message.jobId(), ex);
                    } else {
                        log.debug("[AI 리뷰 Kafka 발행 성공] jobId={}, partition={}, offset={}",
                                message.jobId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
        return future;
    }
}