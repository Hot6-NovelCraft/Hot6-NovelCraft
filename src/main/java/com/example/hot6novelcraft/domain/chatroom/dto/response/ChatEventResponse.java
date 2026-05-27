package com.example.hot6novelcraft.domain.chatroom.dto.response;

/**
 * WebSocket 토픽(/topic/chat/{roomId})으로 브로드캐스트되는 통합 이벤트 DTO
 * eventType: "MESSAGE" | "READ" | "LEAVE"
 */
public record ChatEventResponse(
        String eventType,
        Long userId,
        Long roomId,
        ChatMessageResponse message
) {
    /**
     * 새 메시지 이벤트
     */
    public static ChatEventResponse message(ChatMessageResponse msg) {
        return new ChatEventResponse("MESSAGE", msg.senderId(), msg.roomId(), msg);
    }

    /**
     * 읽음 처리 이벤트 — 누가(userId) 읽었는지 전파
     */
    public static ChatEventResponse read(Long roomId, Long readerId) {
        return new ChatEventResponse("READ", readerId, roomId, null);
    }

    /**
     * 나가기 이벤트 — 누가(userId) 나갔는지 전파
     */
    public static ChatEventResponse leave(Long roomId, Long userId) {
        return new ChatEventResponse("LEAVE", userId, roomId, null);
    }
}
