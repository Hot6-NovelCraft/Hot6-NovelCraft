package com.example.hot6novelcraft.domain.coverai.producer;

import com.example.hot6novelcraft.domain.coverai.dto.event.CoverGenerationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoverGenerationProducer {

    private final KafkaTemplate<String, CoverGenerationEvent> coverKafkaTemplate;

    @Value("${cover.kafka.topic}")
    private String topic;

    public void publish(CoverGenerationEvent event) {
        try {
            coverKafkaTemplate.send(topic, String.valueOf(event.novelId()), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[Kafka] 표지 생성 발행 실패 jobId={}", event.jobId(), ex);
                        } else {
                            log.info("[Kafka] 표지 생성 발행 성공 jobId={} partition={}",
                                    event.jobId(), result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            log.error("[Kafka] 표지 생성 발행 요청 실패 jobId={}", event.jobId(), e);
        }
    }
}