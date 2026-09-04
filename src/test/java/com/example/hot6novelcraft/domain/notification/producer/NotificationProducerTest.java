package com.example.hot6novelcraft.domain.notification.producer;

import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationProducer 테스트")
class NotificationProducerTest {

    @InjectMocks
    private NotificationProducer notificationProducer;

    @Mock
    private KafkaTemplate<String, NotificationEvent> notificationKafkaTemplate;

    private static final String TOPIC = "test-notification-topic";
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationProducer, "topic", TOPIC);
    }

    @Test
    @DisplayName("성공 - Kafka 발행 정상 처리")
    void publish_success() {
        // given
        NotificationEvent event = NotificationEvent.pointCharge(USER_ID, 10_000L, 50_000L);
        given(notificationKafkaTemplate.send(eq(TOPIC), eq(String.valueOf(USER_ID)), eq(event)))
                .willReturn(CompletableFuture.completedFuture(null));

        // when
        notificationProducer.publish(event);

        // then
        verify(notificationKafkaTemplate, times(1)).send(TOPIC, String.valueOf(USER_ID), event);
    }

    @Test
    @DisplayName("KafkaTemplate.send() 비동기 실패: 예외 삼킴 (best-effort)")
    void publish_kafkaSendAsyncFail_exceptionSuppressed() {
        // given
        NotificationEvent event = NotificationEvent.pointCharge(USER_ID, 10_000L, 50_000L);
        given(notificationKafkaTemplate.send(anyString(), anyString(), any(NotificationEvent.class)))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 브로커 연결 실패")));

        // when & then - 예외가 밖으로 전파되지 않아야 함
        assertThatCode(() -> notificationProducer.publish(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("KafkaTemplate.send() 동기 예외 발생: 예외 삼킴 (best-effort)")
    void publish_kafkaSendSyncThrows_exceptionSuppressed() {
        // given
        NotificationEvent event = NotificationEvent.pointCharge(USER_ID, 10_000L, 50_000L);
        given(notificationKafkaTemplate.send(anyString(), anyString(), any(NotificationEvent.class)))
                .willThrow(new RuntimeException("Kafka 시리얼라이즈 오류"));

        // when & then
        assertThatCode(() -> notificationProducer.publish(event))
                .doesNotThrowAnyException();
    }
}
