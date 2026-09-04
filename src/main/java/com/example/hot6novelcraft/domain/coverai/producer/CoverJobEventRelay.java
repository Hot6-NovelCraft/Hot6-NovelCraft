package com.example.hot6novelcraft.domain.coverai.producer;

import com.example.hot6novelcraft.domain.coverai.dto.event.CoverGenerationEvent;
import com.example.hot6novelcraft.domain.coverai.dto.event.CoverJobCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoverJobEventRelay {

    private final CoverGenerationProducer coverGenerationProducer;

    // 트랜잭션 커밋 후에만 Kafka 발행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCoverJobCreated(CoverJobCreatedEvent event) {
        log.info("[Cover] 트랜잭션 커밋 후 Kafka 발행 jobId={}", event.jobId());
        coverGenerationProducer.publish(
                new CoverGenerationEvent(event.jobId(), event.novelId(), event.userId())
        );
    }
}