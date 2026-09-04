package com.example.hot6novelcraft.domain.notification.producer;

import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventRelay 테스트")
class NotificationEventRelayTest {

    @InjectMocks
    private NotificationEventRelay notificationEventRelay;

    @Mock
    private NotificationProducer notificationProducer;

    @Test
    @DisplayName("relay() 호출 시 NotificationProducer.publish()에 이벤트 전달")
    void relay_delegatesToProducer() {
        // given
        NotificationEvent event = NotificationEvent.pointCharge(1L, 10_000L, 50_000L);

        // when
        notificationEventRelay.relay(event);

        // then
        verify(notificationProducer, times(1)).publish(event);
    }
}
