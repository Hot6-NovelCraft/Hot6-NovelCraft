package com.example.hot6novelcraft.domain.notification.producer;

import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션 커밋 이후에 Kafka 알림을 발행한다.
 *
 * @Transactional 메서드 안에서 ApplicationEventPublisher.publishEvent()로 이벤트를 등록하면
 * 커밋이 완료된 시점에 이 리스너가 Kafka에 메시지를 전달한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventRelay {

    private final NotificationProducer notificationProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(NotificationEvent event) {
        log.debug("[알림 릴레이] 트랜잭션 커밋 후 Kafka 발행 userId={} type={}", event.userId(), event.type());
        notificationProducer.publish(event);
    }
}
