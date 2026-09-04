package com.example.hot6novelcraft.domain.chatroom.controller;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.chatroom.chatredispubsub.ChatRedisPublisher;
import com.example.hot6novelcraft.domain.chatroom.dto.request.ChatMessageRequest;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatEventResponse;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatMessageResponse;
import com.example.hot6novelcraft.domain.chatroom.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatRedisPublisher chatRedisPublisher;

    /**
     * 메시지 전송
     * - 클라이언트: STOMP SEND /app/chat/{roomId}
     * - 브로드캐스트: /topic/chat/{roomId}
     * - principal.getName() = StompChannelInterceptor에서 설정한 userId
     */
    @MessageMapping("/chat/{roomId}")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Valid @Payload ChatMessageRequest request,
            Principal principal) {
        Long senderId = Long.parseLong(principal.getName());

        ChatMessageResponse response = chatService.saveMessage(roomId, senderId, request);
        chatRedisPublisher.publish(roomId, ChatEventResponse.message(response));

        log.info("[WebSocket] 메시지 전송 roomId={} senderId={}", roomId, senderId);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(ServiceErrorException e) {
        return e.getMessage();
    }
}
