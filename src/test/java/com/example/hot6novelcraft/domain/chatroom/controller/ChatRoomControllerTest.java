package com.example.hot6novelcraft.domain.chatroom.controller;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.ChatExceptionEnum;
import com.example.hot6novelcraft.domain.chatmessage.entity.enums.MessageType;
import com.example.hot6novelcraft.domain.chatroom.chatredispubsub.ChatRedisPublisher;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatMessageResponse;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatRoomResponse;
import com.example.hot6novelcraft.domain.chatroom.service.ChatService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatRoomController 테스트")
class ChatRoomControllerTest {

    @InjectMocks
    private ChatRoomController chatRoomController;

    @Mock
    private ChatService chatService;

    @Mock
    private ChatRedisPublisher chatRedisPublisher;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 100L;
    private static final Long MENTORSHIP_ID = 10L;

    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatRoomController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver()
                )
                .build();

        User user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        given(user.getRole()).willReturn(com.example.hot6novelcraft.domain.user.entity.enums.UserRole.READER);
        given(user.getPassword()).willReturn("password");
        given(user.getEmail()).willReturn("test@test.com");

        userDetails = new UserDetailsImpl(user, null);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ChatRoomResponse createChatRoomResponse(Long id, Long mentorshipId) {
        return new ChatRoomResponse(id, mentorshipId, 999L, 1000L, 0, LocalDateTime.now());
    }

    private ChatMessageResponse createMessageResponse(Long id, Long senderId, String content) {
        return new ChatMessageResponse(
                id, ROOM_ID, senderId, content,
                MessageType.TEXT,
                null, false, LocalDateTime.now()
        );
    }

    // =========================================================
    // getOrCreateChatRoom() - 채팅방 생성 또는 조회
    // =========================================================
    @Nested
    @DisplayName("POST /api/chatrooms - 채팅방 생성 또는 조회")
    class GetOrCreateChatRoomTest {

        @Test
        @DisplayName("성공 - 채팅방 생성 또는 조회")
        void getOrCreateChatRoom_success() throws Exception {
            // given
            ChatRoomResponse response = createChatRoomResponse(ROOM_ID, MENTORSHIP_ID);
            given(chatService.getOrCreateChatRoom(MENTORSHIP_ID, USER_ID)).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/chatrooms")
                            .param("mentorshipId", String.valueOf(MENTORSHIP_ID))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.message").value("채팅방 조회/생성 완료"))
                    .andExpect(jsonPath("$.data.id").value(ROOM_ID))
                    .andExpect(jsonPath("$.data.mentorshipId").value(MENTORSHIP_ID));

            verify(chatService, times(1)).getOrCreateChatRoom(MENTORSHIP_ID, USER_ID);
        }

        @Test
        @DisplayName("실패 - mentorshipId 파라미터 누락")
        void getOrCreateChatRoom_missingMentorshipId_badRequest() throws Exception {
            // when & then
            mockMvc.perform(post("/api/chatrooms")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verify(chatService, never()).getOrCreateChatRoom(anyLong(), anyLong());
        }

        @Test
        @DisplayName("실패 - Mentorship 없을 시 예외")
        void getOrCreateChatRoom_mentorshipNotFound_throwsException() throws Exception {
            // given
            given(chatService.getOrCreateChatRoom(MENTORSHIP_ID, USER_ID))
                    .willThrow(new ServiceErrorException(ChatExceptionEnum.ERR_CHATROOM_NOT_FOUND));

            // when & then
            mockMvc.perform(post("/api/chatrooms")
                            .param("mentorshipId", String.valueOf(MENTORSHIP_ID))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================
    // getMyChatRooms() - 내 채팅방 목록 조회
    // =========================================================
    @Nested
    @DisplayName("GET /api/chatrooms - 내 채팅방 목록 조회")
    class GetMyChatRoomsTest {

        @Test
        @DisplayName("성공 - 내 채팅방 목록 조회")
        void getMyChatRooms_success() throws Exception {
            // given
            List<ChatRoomResponse> responses = List.of(
                    createChatRoomResponse(1L, 10L),
                    createChatRoomResponse(2L, 20L),
                    createChatRoomResponse(3L, 30L)
            );
            given(chatService.getMyChatRooms(USER_ID)).willReturn(responses);

            // when & then
            mockMvc.perform(get("/api/chatrooms")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.message").value("채팅방 목록 조회 완료"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(3));

            verify(chatService, times(1)).getMyChatRooms(USER_ID);
        }

        @Test
        @DisplayName("성공 - 채팅방 없을 시 빈 리스트 반환")
        void getMyChatRooms_noChatRooms_returnsEmptyList() throws Exception {
            // given
            given(chatService.getMyChatRooms(USER_ID)).willReturn(List.of());

            // when & then
            mockMvc.perform(get("/api/chatrooms")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // =========================================================
    // getMessages() - 채팅방 메시지 히스토리 조회
    // =========================================================
    @Nested
    @DisplayName("GET /api/chatrooms/{roomId}/messages - 메시지 히스토리 조회")
    class GetMessagesTest {

        @Test
        @DisplayName("성공 - 메시지 히스토리 조회 (페이징)")
        void getMessages_success() throws Exception {
            // given
            List<ChatMessageResponse> messages = List.of(
                    createMessageResponse(1L, USER_ID, "메시지 1"),
                    createMessageResponse(2L, USER_ID, "메시지 2"),
                    createMessageResponse(3L, USER_ID, "메시지 3")
            );
            Pageable pageable = PageRequest.of(0, 30);
            Page<ChatMessageResponse> page = new PageImpl<>(messages, pageable, 3);
            PageResponse<ChatMessageResponse> pageResponse = PageResponse.register(page);

            given(chatService.getMessages(eq(ROOM_ID), eq(USER_ID), any(Pageable.class)))
                    .willReturn(pageResponse);

            // when & then
            mockMvc.perform(get("/api/chatrooms/{roomId}/messages", ROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.message").value("메시지 조회 완료"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(3))
                    .andExpect(jsonPath("$.data.totalElements").value(3));

            verify(chatService, times(1)).getMessages(eq(ROOM_ID), eq(USER_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("성공 - 페이지 파라미터 전달")
        void getMessages_withPageParams_success() throws Exception {
            // given
            PageResponse<ChatMessageResponse> emptyPage = new PageResponse<>(List.of(), 1, 1, 0L, 20, true);
            given(chatService.getMessages(eq(ROOM_ID), eq(USER_ID), any(Pageable.class)))
                    .willReturn(emptyPage);

            // when & then
            mockMvc.perform(get("/api/chatrooms/{roomId}/messages", ROOM_ID)
                            .param("page", "1")
                            .param("size", "20")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(chatService, times(1)).getMessages(eq(ROOM_ID), eq(USER_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("실패 - 채팅방 멤버가 아닐 시 예외")
        void getMessages_notMember_throwsException() throws Exception {
            // given
            given(chatService.getMessages(eq(ROOM_ID), eq(USER_ID), any(Pageable.class)))
                    .willThrow(new ServiceErrorException(ChatExceptionEnum.ERR_NOT_PARTICIPANT));

            // when & then
            mockMvc.perform(get("/api/chatrooms/{roomId}/messages", ROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================
    // leaveChatRoom() - 채팅방 나가기
    // =========================================================
    @Nested
    @DisplayName("DELETE /api/chatrooms/{roomId}/leave - 채팅방 나가기")
    class LeaveChatRoomTest {

        @Test
        @DisplayName("성공 - 채팅방 나가기 + LEAVE 이벤트 퍼블리시")
        void leaveChatRoom_success() throws Exception {
            // given
            doNothing().when(chatService).leaveChatRoom(ROOM_ID, USER_ID);
            doNothing().when(chatRedisPublisher).publish(anyLong(), any());

            // when & then
            mockMvc.perform(delete("/api/chatrooms/{roomId}/leave", ROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.message").value("채팅방 나가기 완료"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(chatService, times(1)).leaveChatRoom(ROOM_ID, USER_ID);
            verify(chatRedisPublisher, times(1)).publish(anyLong(), any());
        }

        @Test
        @DisplayName("검증 - leaveChatRoom 후 Redis 퍼블리시")
        void leaveChatRoom_publishesAfterLeave() throws Exception {
            // given
            doNothing().when(chatService).leaveChatRoom(ROOM_ID, USER_ID);
            doNothing().when(chatRedisPublisher).publish(anyLong(), any());

            // when
            mockMvc.perform(delete("/api/chatrooms/{roomId}/leave", ROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            // then - leaveChatRoom 후 publish 호출 순서 검증
            var inOrder = inOrder(chatService, chatRedisPublisher);
            inOrder.verify(chatService).leaveChatRoom(ROOM_ID, USER_ID);
            inOrder.verify(chatRedisPublisher).publish(anyLong(), any());
        }

        @Test
        @DisplayName("실패 - 채팅방 멤버가 아닐 시 예외")
        void leaveChatRoom_notMember_throwsException() throws Exception {
            // given
            doThrow(new ServiceErrorException(ChatExceptionEnum.ERR_NOT_PARTICIPANT))
                    .when(chatService).leaveChatRoom(ROOM_ID, USER_ID);

            // when & then
            mockMvc.perform(delete("/api/chatrooms/{roomId}/leave", ROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verify(chatRedisPublisher, never()).publish(anyLong(), any());
        }
    }

    // =========================================================
    // markAsRead() - 채팅방 입장 시 읽음 처리
    // =========================================================
    @Nested
    @DisplayName("PATCH /api/chatrooms/{roomId}/read - 읽음 처리")
    class MarkAsReadTest {

        @Test
        @DisplayName("성공 - 읽음 처리 + READ 이벤트 퍼블리시")
        void markAsRead_success() throws Exception {
            // given
            doNothing().when(chatService).markMessagesAsRead(ROOM_ID, USER_ID);
            doNothing().when(chatRedisPublisher).publish(anyLong(), any());

            // when & then
            mockMvc.perform(patch("/api/chatrooms/{roomId}/read", ROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.message").value("읽음 처리 완료"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(chatService, times(1)).markMessagesAsRead(ROOM_ID, USER_ID);
            verify(chatRedisPublisher, times(1)).publish(anyLong(), any());
        }

        @Test
        @DisplayName("검증 - markMessagesAsRead 후 Redis 퍼블리시")
        void markAsRead_publishesAfterMark() throws Exception {
            // given
            doNothing().when(chatService).markMessagesAsRead(ROOM_ID, USER_ID);
            doNothing().when(chatRedisPublisher).publish(anyLong(), any());

            // when
            mockMvc.perform(patch("/api/chatrooms/{roomId}/read", ROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            // then - markMessagesAsRead 후 publish 호출 순서 검증
            var inOrder = inOrder(chatService, chatRedisPublisher);
            inOrder.verify(chatService).markMessagesAsRead(ROOM_ID, USER_ID);
            inOrder.verify(chatRedisPublisher).publish(anyLong(), any());
        }

        @Test
        @DisplayName("실패 - 채팅방 멤버가 아닐 시 예외")
        void markAsRead_notMember_throwsException() throws Exception {
            // given
            doThrow(new ServiceErrorException(ChatExceptionEnum.ERR_NOT_PARTICIPANT))
                    .when(chatService).markMessagesAsRead(ROOM_ID, USER_ID);

            // when & then
            mockMvc.perform(patch("/api/chatrooms/{roomId}/read", ROOM_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verify(chatRedisPublisher, never()).publish(anyLong(), any());
        }
    }
}
