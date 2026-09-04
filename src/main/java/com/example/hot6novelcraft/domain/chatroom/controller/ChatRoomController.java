package com.example.hot6novelcraft.domain.chatroom.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.chatroom.chatredispubsub.ChatRedisPublisher;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatEventResponse;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatMessageResponse;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatRoomResponse;
import com.example.hot6novelcraft.domain.chatroom.service.ChatService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chatrooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatService chatService;
    private final ChatRedisPublisher chatRedisPublisher;

    /**
     * 채팅방 생성 또는 기존 채팅방 반환
     * - 동일 mentorshipId로 요청 시 기존 채팅방 반환
     */
    @PostMapping
    public BaseResponse<ChatRoomResponse> getOrCreateChatRoom(
            @RequestParam Long mentorshipId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUser().getId();
        return BaseResponse.success("200", "채팅방 조회/생성 완료", chatService.getOrCreateChatRoom(mentorshipId, userId));
    }

    /**
     * 내 채팅방 목록 조회
     */
    @GetMapping
    public BaseResponse<List<ChatRoomResponse>> getMyChatRooms(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUser().getId();
        return BaseResponse.success("200", "채팅방 목록 조회 완료", chatService.getMyChatRooms(userId));
    }

    /**
     * 채팅방 메시지 히스토리 조회 (최신순)
     */
    @GetMapping("/{roomId}/messages")
    public BaseResponse<PageResponse<ChatMessageResponse>> getMessages(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PageableDefault(size = 30) Pageable pageable) {
        Long userId = userDetails.getUser().getId();
        return BaseResponse.success("200", "메시지 조회 완료", chatService.getMessages(roomId, userId, pageable));
    }

    /**
     * 채팅방 나가기 — leftAt 기록 후 LEAVE 이벤트 브로드캐스트 (Fix 3)
     */
    @DeleteMapping("/{roomId}/leave")
    public BaseResponse<Void> leaveChatRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUser().getId();
        chatService.leaveChatRoom(roomId, userId);
        chatRedisPublisher.publish(roomId, ChatEventResponse.leave(roomId, userId));
        return BaseResponse.success("200", "채팅방 나가기 완료", null);
    }

    /**
     * 채팅방 입장 시 읽음 처리 — READ 이벤트 브로드캐스트 (Fix 2)
     */
    @PatchMapping("/{roomId}/read")
    public BaseResponse<Void> markAsRead(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUser().getId();
        chatService.markMessagesAsRead(roomId, userId);
        chatRedisPublisher.publish(roomId, ChatEventResponse.read(roomId, userId));
        return BaseResponse.success("200", "읽음 처리 완료", null);
    }
}
