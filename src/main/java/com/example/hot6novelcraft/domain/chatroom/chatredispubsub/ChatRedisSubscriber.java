package com.example.hot6novelcraft.domain.chatroom.chatredispubsub;

import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatEventResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRedisSubscriber implements MessageListener {

    private final RedisMessageListenerContainer listenerContainer;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, new PatternTopic("chat:room:*"));
        log.info("[Redis] 채팅 채널 구독 시작: chat:room:*");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatEventResponse event = objectMapper.readValue(message.getBody(), ChatEventResponse.class);
            messagingTemplate.convertAndSend("/topic/chat/" + event.roomId(), event);
            log.info("[Redis] 메시지 수신 → 브로드캐스트 roomId={} eventType={}", event.roomId(), event.eventType());
        } catch (Exception e) {
            log.error("[Redis] 메시지 처리 실패: {}", e.getMessage());
        }
    }
}
