package com.example.hot6novelcraft.domain.chatroom.dto.response;

import com.example.hot6novelcraft.domain.chatmessage.entity.ChatMessage;
import com.example.hot6novelcraft.domain.chatmessage.entity.enums.MessageType;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long senderId,
        String content,
        MessageType messageType,
        String fileUrl,
        boolean isRead,
        LocalDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getSenderId(),
                message.getContent(),
                message.getMessageType(),
                message.getFileUrl(),
                message.isRead(),
                message.getCreatedAt()
        );
    }
}
