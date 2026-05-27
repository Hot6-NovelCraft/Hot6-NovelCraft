package com.example.hot6novelcraft.domain.chatroom.dto.request;

import com.example.hot6novelcraft.domain.chatmessage.entity.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatMessageRequest(
        @NotBlank(message = "메시지 내용은 필수입니다")
        String content,

        @NotNull(message = "메시지 타입은 필수입니다")
        MessageType messageType,

        String fileUrl  // FILE 타입 메시지일 때만 사용 (선택적)
) {
}
