package com.example.hot6novelcraft.domain.chatroom.controller;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.ChatExceptionEnum;
import com.example.hot6novelcraft.domain.chatmessage.entity.enums.MessageType;
import com.example.hot6novelcraft.domain.chatroom.chatredispubsub.ChatRedisPublisher;
import com.example.hot6novelcraft.domain.chatroom.dto.request.ChatMessageRequest;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatEventResponse;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatMessageResponse;
import com.example.hot6novelcraft.domain.chatroom.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatController 테스트")
class ChatControllerTest {

    @InjectMocks
    private ChatController chatController;

    @Mock
    private ChatService chatService;

    @Mock
    private ChatRedisPublisher chatRedisPublisher;

    @Mock
    private Principal principal;

    private static final Long ROOM_ID = 1L;
    private static final Long SENDER_ID = 100L;
    private static final Long RECEIVER_ID = 200L;

    private ChatMessageRequest createMessageRequest(String content) {
        return new ChatMessageRequest(content, MessageType.TEXT, null);
    }

    private ChatMessageResponse createMessageResponse(Long id, Long senderId, String content) {
        return new ChatMessageResponse(
                id,
                ROOM_ID,
                senderId,
                content,
                MessageType.TEXT,
                null,
                false,
                LocalDateTime.now()
        );
    }

    // =========================================================
    // sendMessage() - 메시지 전송 (WebSocket @MessageMapping)
    // =========================================================
    @Nested
    @DisplayName("sendMessage() - WebSocket 메시지 전송")
    class SendMessageTest {

        @Test
        @DisplayName("성공 - 메시지 저장 및 Redis 퍼블리시")
        void sendMessage_success() {
            // given
            ChatMessageRequest request = createMessageRequest("안녕하세요");
            ChatMessageResponse response = createMessageResponse(1L, SENDER_ID, "안녕하세요");

            given(principal.getName()).willReturn(String.valueOf(SENDER_ID));
            given(chatService.saveMessage(ROOM_ID, SENDER_ID, request)).willReturn(response);
            doNothing().when(chatRedisPublisher).publish(anyLong(), any(ChatEventResponse.class));

            // when
            chatController.sendMessage(ROOM_ID, request, principal);

            // then
            verify(chatService, times(1)).saveMessage(ROOM_ID, SENDER_ID, request);
            verify(chatRedisPublisher, times(1)).publish(eq(ROOM_ID), any(ChatEventResponse.class));
        }

        @Test
        @DisplayName("검증 - Principal에서 userId 추출")
        void sendMessage_extractsUserIdFromPrincipal() {
            // given
            ChatMessageRequest request = createMessageRequest("테스트 메시지");
            ChatMessageResponse response = createMessageResponse(1L, SENDER_ID, "테스트 메시지");

            given(principal.getName()).willReturn(String.valueOf(SENDER_ID));
            given(chatService.saveMessage(ROOM_ID, SENDER_ID, request)).willReturn(response);

            // when
            chatController.sendMessage(ROOM_ID, request, principal);

            // then
            verify(principal, times(1)).getName();
            verify(chatService, times(1)).saveMessage(eq(ROOM_ID), eq(SENDER_ID), eq(request));
        }

        @Test
        @DisplayName("검증 - 메시지 저장 후 Redis 퍼블리시")
        void sendMessage_publishesAfterSave() {
            // given
            ChatMessageRequest request = createMessageRequest("안녕하세요");
            ChatMessageResponse response = createMessageResponse(1L, SENDER_ID, "안녕하세요");

            given(principal.getName()).willReturn(String.valueOf(SENDER_ID));
            given(chatService.saveMessage(ROOM_ID, SENDER_ID, request)).willReturn(response);

            // when
            chatController.sendMessage(ROOM_ID, request, principal);

            // then - saveMessage 후 publish 호출 순서 검증
            var inOrder = inOrder(chatService, chatRedisPublisher);
            inOrder.verify(chatService).saveMessage(ROOM_ID, SENDER_ID, request);
            inOrder.verify(chatRedisPublisher).publish(eq(ROOM_ID), any(ChatEventResponse.class));
        }

        @Test
        @DisplayName("성공 - 파일 첨부 메시지 전송")
        void sendMessage_withFileUrl_success() {
            // given
            String fileUrl = "https://s3.example.com/files/document.pdf";
            ChatMessageRequest request = new ChatMessageRequest("파일을 보냅니다", MessageType.FILE, fileUrl);
            ChatMessageResponse response = new ChatMessageResponse(
                    1L, ROOM_ID, SENDER_ID, "파일을 보냅니다",
                    MessageType.FILE, fileUrl, false, LocalDateTime.now()
            );

            given(principal.getName()).willReturn(String.valueOf(SENDER_ID));
            given(chatService.saveMessage(ROOM_ID, SENDER_ID, request)).willReturn(response);
            doNothing().when(chatRedisPublisher).publish(anyLong(), any(ChatEventResponse.class));

            // when
            chatController.sendMessage(ROOM_ID, request, principal);

            // then
            verify(chatService, times(1)).saveMessage(ROOM_ID, SENDER_ID, request);
            verify(chatRedisPublisher, times(1)).publish(eq(ROOM_ID), any(ChatEventResponse.class));
        }

        @Test
        @DisplayName("성공 - 여러 방에 메시지 전송 (각 roomId로 퍼블리시)")
        void sendMessage_differentRooms_publishesToDifferentTopics() {
            // given
            ChatMessageRequest request = createMessageRequest("안녕하세요");
            ChatMessageResponse response1 = createMessageResponse(1L, SENDER_ID, "안녕하세요");
            ChatMessageResponse response2 = createMessageResponse(2L, SENDER_ID, "안녕하세요");

            given(principal.getName()).willReturn(String.valueOf(SENDER_ID));
            given(chatService.saveMessage(1L, SENDER_ID, request)).willReturn(response1);
            given(chatService.saveMessage(2L, SENDER_ID, request)).willReturn(response2);

            // when
            chatController.sendMessage(1L, request, principal);
            chatController.sendMessage(2L, request, principal);

            // then
            verify(chatRedisPublisher, times(1)).publish(eq(1L), any(ChatEventResponse.class));
            verify(chatRedisPublisher, times(1)).publish(eq(2L), any(ChatEventResponse.class));
        }
    }

    // =========================================================
    // handleException() - WebSocket 예외 처리
    // =========================================================
    @Nested
    @DisplayName("handleException() - WebSocket 예외 처리")
    class HandleExceptionTest {

        @Test
        @DisplayName("성공 - ServiceErrorException 메시지 반환")
        void handleException_returnsErrorMessage() {
            // given
            ServiceErrorException exception = new ServiceErrorException(ChatExceptionEnum.ERR_CHATROOM_NOT_FOUND);

            // when
            String result = chatController.handleException(exception);

            // then
            assertThat(result).isEqualTo(ChatExceptionEnum.ERR_CHATROOM_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("성공 - 다양한 예외 메시지 처리")
        void handleException_handlesDifferentExceptions() {
            // given
            ServiceErrorException exception1 = new ServiceErrorException(ChatExceptionEnum.ERR_CHATROOM_NOT_FOUND);
            ServiceErrorException exception2 = new ServiceErrorException(ChatExceptionEnum.ERR_NOT_PARTICIPANT);

            // when
            String result1 = chatController.handleException(exception1);
            String result2 = chatController.handleException(exception2);

            // then
            assertThat(result1).isEqualTo(ChatExceptionEnum.ERR_CHATROOM_NOT_FOUND.getMessage());
            assertThat(result2).isEqualTo(ChatExceptionEnum.ERR_NOT_PARTICIPANT.getMessage());
        }
    }
}
