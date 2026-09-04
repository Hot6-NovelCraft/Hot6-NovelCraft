package com.example.hot6novelcraft.domain.chatmessage.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.chatmessage.entity.enums.MessageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_messages")
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long senderId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String fileUrl;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private MessageType messageType;

    @Column(nullable = false)
    private boolean isRead;

    private ChatMessage(Long roomId, Long senderId, String content, MessageType messageType) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.messageType = messageType;
        this.isRead = false;
    }

    public static ChatMessage create(Long roomId, Long senderId, String content, MessageType messageType) {
        return new ChatMessage(roomId, senderId, content, messageType);
    }

    public static ChatMessage createWithFile(Long roomId, Long senderId, String content, MessageType messageType, String fileUrl) {
        ChatMessage message = new ChatMessage(roomId, senderId, content, messageType);
        message.fileUrl = fileUrl;
        return message;
    }
}
