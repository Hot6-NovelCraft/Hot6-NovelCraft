package com.example.hot6novelcraft.domain.chatroom.dto.response;

import com.example.hot6novelcraft.domain.chatroom.entity.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResponse(Long id, Long mentorshipId, Long mentorId, Long menteeId, long unreadCount, LocalDateTime createdAt) {

    public static ChatRoomResponse from(ChatRoom room) {
        return new ChatRoomResponse(room.getId(), room.getMentorshipId(), room.getMentorId(), room.getMenteeId(), 0, room.getCreatedAt());
    }

    public static ChatRoomResponse from(ChatRoom room, long unreadCount) {
        return new ChatRoomResponse(room.getId(), room.getMentorshipId(), room.getMentorId(), room.getMenteeId(), unreadCount, room.getCreatedAt());
    }
}
