package com.example.hot6novelcraft.domain.aichat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ChatClient customerServiceChatClient;
    private final ChatMemory chatMemory;

    public Flux<String> chat(Long userId, String message) {
        // 사용자별 고유 대화 ID: 같은 userId면 항상 같은 히스토리를 불러옴
        String conversationId = "ai-support:" + userId;

        return customerServiceChatClient.prompt()
                .user(message)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(conversationId)
                        .build())
                .stream()
                .content();
    }

    public void clearSession(Long userId) {
        String conversationId = "ai-support:" + userId;
        chatMemory.clear(conversationId);
    }
}