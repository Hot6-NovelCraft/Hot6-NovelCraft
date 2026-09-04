package com.example.hot6novelcraft.domain.notification.producer;

import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, NotificationEvent> notificationKafkaTemplate;

    @Value("${notification.kafka.topic}")
    private String topic;

    public void publish(NotificationEvent event) {
        try {
            notificationKafkaTemplate.send(topic, String.valueOf(event.userId()), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[Kafka] 알림 발행 실패 userId={} type={}", event.userId(), event.type(), ex);
                        } else {
                            log.debug("[Kafka] 알림 발행 성공 userId={} type={} partition={}",
                                    event.userId(), event.type(), result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            log.error("[Kafka] 알림 발행 요청 실패 userId={} type={}", event.userId(), event.type(), e);
        }
    }
}
