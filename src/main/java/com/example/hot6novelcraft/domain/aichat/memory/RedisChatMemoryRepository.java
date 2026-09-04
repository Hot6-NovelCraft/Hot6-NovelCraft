package com.example.hot6novelcraft.domain.aichat.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.chat.memory.redis.key-prefix:ai:chat:memory:}")
    private String keyPrefix;

    @Value("${ai.chat.memory.redis.ttl-days:7}")
    private long ttlDays;

    /**
     * USER, ASSISTANT 메시지를 Redis에 저장하는 직렬화 형식
     * Spring AI Message 인터페이스는 다형성이 있어 직접 직렬화가 복잡하므로
     * role/content 형태의 단순 record로 변환해서 저장한다.
     */
    private record MessageRecord(String role, String content) {
    }

    @Override
    public List<String> findConversationIds() {
        return StreamSupport.stream(
                        redissonClient.getKeys().getKeysByPattern(keyPrefix + "*").spliterator(), false)
                .map(key -> key.substring(keyPrefix.length()))
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        RList<String> list = redissonClient.getList(keyPrefix + conversationId);
        return list.readAll().stream()
                .map(this::deserialize)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * MessageWindowChatMemory가 이 메서드를 호출해서 전체 히스토리를 덮어씌운다.
     * 매 요청 후 최신 N턴이 저장되며, TTL로 자동 만료된다.
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = keyPrefix + conversationId;
        RList<String> list = redissonClient.getList(key);

        List<String> serialized = messages.stream()
                .map(this::serialize)
                .filter(Objects::nonNull)
                .toList();

        list.clear();
        list.addAll(serialized);
        list.expire(Duration.ofDays(ttlDays));

        log.debug("Redis 대화 메모리 저장 완료: conversationId={}, 메시지 수={}", conversationId, serialized.size());
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redissonClient.getList(keyPrefix + conversationId).delete();
        log.debug("Redis 대화 메모리 삭제: conversationId={}", conversationId);
    }

    private String serialize(Message message) {
        try {
            MessageRecord record = new MessageRecord(
                    message.getMessageType().name(),
                    message.getText()
            );
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            log.error("메시지 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    private Message deserialize(String json) {
        try {
            MessageRecord record = objectMapper.readValue(json, MessageRecord.class);
            return switch (MessageType.valueOf(record.role())) {
                case USER -> new UserMessage(record.content());
                case ASSISTANT -> new AssistantMessage(record.content());
                case SYSTEM -> new SystemMessage(record.content());
                default -> null;
            };
        } catch (Exception e) {
            log.error("메시지 역직렬화 실패: {}", e.getMessage());
            return null;
        }
    }
}
