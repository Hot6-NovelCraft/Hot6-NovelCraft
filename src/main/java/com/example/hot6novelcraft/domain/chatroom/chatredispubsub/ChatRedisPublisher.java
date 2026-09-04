package com.example.hot6novelcraft.domain.chatroom.chatredispubsub;

import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatEventResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRedisPublisher {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(Long roomId, ChatEventResponse event) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                byte[] channel = ("chat:room:" + roomId).getBytes(StandardCharsets.UTF_8);
                byte[] message = objectMapper.writeValueAsBytes(event);
                redisTemplate.execute((RedisCallback<Long>) conn -> conn.publish(channel, message));

                if (attempt > 0) {
                    log.info("[Redis] 이벤트 발행 성공 (재시도 {}회) roomId={}", attempt, roomId);
                }
                return; // 성공 시 종료

            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn("[Redis] 이벤트 발행 실패 (재시도 {}/{}) roomId={}: {}",
                            attempt, MAX_RETRY_ATTEMPTS, roomId, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 모든 재시도 실패 (스택트레이스 포함하여 디버깅 용이)
        log.error("[Redis] 이벤트 발행 최종 실패 ({}회 재시도) roomId={}",
                MAX_RETRY_ATTEMPTS, roomId, lastException);
    }
}
