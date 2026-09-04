package com.example.hot6novelcraft.domain.chatroom.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.ChatExceptionEnum;
import com.example.hot6novelcraft.domain.chatmessage.entity.ChatMessage;
import com.example.hot6novelcraft.domain.chatmessage.entity.enums.MessageType;
import com.example.hot6novelcraft.domain.chatmessage.repository.ChatMessageRepository;
import com.example.hot6novelcraft.domain.chatroom.dto.request.ChatMessageRequest;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatMessageResponse;
import com.example.hot6novelcraft.domain.chatroom.dto.response.ChatRoomResponse;
import com.example.hot6novelcraft.domain.chatroom.entity.ChatRoom;
import com.example.hot6novelcraft.domain.chatroom.repository.ChatRoomRepository;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatService 테스트")
class ChatServiceTest {

    @InjectMocks
    private ChatService chatService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MentorshipRepository mentorshipRepository;

    @Mock
    private MentorRepository mentorRepository;

    @Mock
    private UserRepository userRepository;

    private static final Long ROOM_ID = 1L;
    private static final Long MENTORSHIP_ID = 10L;
    private static final Long MENTOR_USER_ID = 100L;
    private static final Long MENTEE_USER_ID = 200L;
    private static final Long MENTOR_ID = 50L; // mentors 테이블의 PK
    private static final String S3_BUCKET = "hot6-novelcraft-chat";
    private static final String S3_REGION = "ap-northeast-2";

    private Mentorship createMockMentorship() {
        Mentorship mentorship = mock(Mentorship.class);
        given(mentorship.getId()).willReturn(MENTORSHIP_ID);
        given(mentorship.getMentorId()).willReturn(MENTOR_ID);
        given(mentorship.getMenteeId()).willReturn(MENTEE_USER_ID);
        return mentorship;
    }

    private Mentor createMockMentor() {
        Mentor mentor = mock(Mentor.class);
        given(mentor.getId()).willReturn(MENTOR_ID);
        given(mentor.getUserId()).willReturn(MENTOR_USER_ID);
        return mentor;
    }

    private User createMockUser(Long id, UserRole role) {
        User user = mock(User.class);
        given(user.getId()).willReturn(id);
        given(user.getRole()).willReturn(role);
        return user;
    }

    private ChatRoom createMockChatRoom(Long id, Long mentorUserId, Long menteeUserId) {
        ChatRoom room = mock(ChatRoom.class);
        given(room.getId()).willReturn(id);
        given(room.getMentorId()).willReturn(mentorUserId);
        given(room.getMenteeId()).willReturn(menteeUserId);
        given(room.isParticipant(mentorUserId)).willReturn(true);
        given(room.isParticipant(menteeUserId)).willReturn(true);
        given(room.hasLeft(anyLong())).willReturn(false);
        return room;
    }

    private ChatMessage createMockChatMessage(Long id, Long roomId, Long senderId, String content) {
        ChatMessage message = mock(ChatMessage.class);
        given(message.getId()).willReturn(id);
        given(message.getRoomId()).willReturn(roomId);
        given(message.getSenderId()).willReturn(senderId);
        given(message.getContent()).willReturn(content);
        given(message.getMessageType()).willReturn(MessageType.TEXT);
        return message;
    }

    // =========================================================
    // getOrCreateChatRoom() - 채팅방 생성 또는 조회
    // =========================================================
    @Nested
    @DisplayName("getOrCreateChatRoom() - 채팅방 생성 또는 조회")
    class GetOrCreateChatRoomTest {

        @Test
        @DisplayName("성공 - 기존 채팅방 반환")
        void getOrCreateChatRoom_existingRoom_returnsRoom() {
            // given
            Mentorship mentorship = createMockMentorship();
            Mentor mentor = createMockMentor();
            ChatRoom existingRoom = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            User mentorUser = createMockUser(MENTOR_USER_ID, UserRole.AUTHOR);
            User menteeUser = createMockUser(MENTEE_USER_ID, UserRole.AUTHOR);

            given(mentorshipRepository.findById(MENTORSHIP_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ID)).willReturn(Optional.of(mentor));
            given(userRepository.findById(MENTOR_USER_ID)).willReturn(Optional.of(mentorUser));
            given(userRepository.findById(MENTEE_USER_ID)).willReturn(Optional.of(menteeUser));
            given(chatRoomRepository.findByMentorshipId(MENTORSHIP_ID)).willReturn(Optional.of(existingRoom));

            // when
            ChatRoomResponse result = chatService.getOrCreateChatRoom(MENTORSHIP_ID, MENTOR_USER_ID);

            // then
            assertThat(result).isNotNull();
            verify(chatRoomRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("성공 - 새 채팅방 생성")
        void getOrCreateChatRoom_noExistingRoom_createsRoom() {
            // given
            Mentorship mentorship = createMockMentorship();
            Mentor mentor = createMockMentor();
            ChatRoom newRoom = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            User mentorUser = createMockUser(MENTOR_USER_ID, UserRole.AUTHOR);
            User menteeUser = createMockUser(MENTEE_USER_ID, UserRole.AUTHOR);

            given(mentorshipRepository.findById(MENTORSHIP_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ID)).willReturn(Optional.of(mentor));
            given(userRepository.findById(MENTOR_USER_ID)).willReturn(Optional.of(mentorUser));
            given(userRepository.findById(MENTEE_USER_ID)).willReturn(Optional.of(menteeUser));
            given(chatRoomRepository.findByMentorshipId(MENTORSHIP_ID)).willReturn(Optional.empty());
            given(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).willReturn(newRoom);

            // when
            ChatRoomResponse result = chatService.getOrCreateChatRoom(MENTORSHIP_ID, MENTEE_USER_ID);

            // then
            assertThat(result).isNotNull();
            verify(chatRoomRepository, times(1)).saveAndFlush(any(ChatRoom.class));
        }

        @Test
        @DisplayName("성공 - 나갔던 방 재입장 시 leftAt 초기화")
        void getOrCreateChatRoom_rejoinAfterLeaving_resetsLeftAt() {
            // given
            Mentorship mentorship = createMockMentorship();
            Mentor mentor = createMockMentor();
            ChatRoom existingRoom = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            User mentorUser = createMockUser(MENTOR_USER_ID, UserRole.AUTHOR);
            User menteeUser = createMockUser(MENTEE_USER_ID, UserRole.AUTHOR);

            given(mentorshipRepository.findById(MENTORSHIP_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ID)).willReturn(Optional.of(mentor));
            given(userRepository.findById(MENTOR_USER_ID)).willReturn(Optional.of(mentorUser));
            given(userRepository.findById(MENTEE_USER_ID)).willReturn(Optional.of(menteeUser));
            given(chatRoomRepository.findByMentorshipId(MENTORSHIP_ID)).willReturn(Optional.of(existingRoom));
            given(existingRoom.hasLeft(MENTOR_USER_ID)).willReturn(true);

            // when
            chatService.getOrCreateChatRoom(MENTORSHIP_ID, MENTOR_USER_ID);

            // then
            verify(existingRoom, times(1)).rejoin(MENTOR_USER_ID);
        }

        @Test
        @DisplayName("동시성 - DataIntegrityViolationException 발생 시 기존 방 반환")
        void getOrCreateChatRoom_concurrentCreation_returnsExisting() {
            // given
            Mentorship mentorship = createMockMentorship();
            Mentor mentor = createMockMentor();
            ChatRoom existingRoom = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            User mentorUser = createMockUser(MENTOR_USER_ID, UserRole.AUTHOR);
            User menteeUser = createMockUser(MENTEE_USER_ID, UserRole.AUTHOR);

            given(mentorshipRepository.findById(MENTORSHIP_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ID)).willReturn(Optional.of(mentor));
            given(userRepository.findById(MENTOR_USER_ID)).willReturn(Optional.of(mentorUser));
            given(userRepository.findById(MENTEE_USER_ID)).willReturn(Optional.of(menteeUser));
            given(chatRoomRepository.findByMentorshipId(MENTORSHIP_ID))
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(existingRoom));
            given(chatRoomRepository.saveAndFlush(any(ChatRoom.class)))
                    .willThrow(new DataIntegrityViolationException("Unique constraint violation"));

            // when
            ChatRoomResponse result = chatService.getOrCreateChatRoom(MENTORSHIP_ID, MENTOR_USER_ID);

            // then
            assertThat(result).isNotNull();
            verify(chatRoomRepository, times(2)).findByMentorshipId(MENTORSHIP_ID);
        }

        @Test
        @DisplayName("실패 - Mentorship 없을 시 예외")
        void getOrCreateChatRoom_mentorshipNotFound_throwsException() {
            // given
            given(mentorshipRepository.findById(MENTORSHIP_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatService.getOrCreateChatRoom(MENTORSHIP_ID, MENTOR_USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_MENTORSHIP_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 참여자가 아닌 사용자 요청 시 예외")
        void getOrCreateChatRoom_notParticipant_throwsException() {
            // given
            Mentorship mentorship = createMockMentorship();
            Mentor mentor = createMockMentor();

            given(mentorshipRepository.findById(MENTORSHIP_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ID)).willReturn(Optional.of(mentor));

            // when & then
            assertThatThrownBy(() -> chatService.getOrCreateChatRoom(MENTORSHIP_ID, 999L))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_NOT_PARTICIPANT.getMessage());
        }

        @Test
        @DisplayName("실패 - 멘토가 AUTHOR 역할이 아닐 시 예외")
        void getOrCreateChatRoom_mentorNotAuthor_throwsException() {
            // given
            Mentorship mentorship = createMockMentorship();
            Mentor mentor = createMockMentor();
            User mentorUser = createMockUser(MENTOR_USER_ID, UserRole.READER);

            given(mentorshipRepository.findById(MENTORSHIP_ID)).willReturn(Optional.of(mentorship));
            given(mentorRepository.findById(MENTOR_ID)).willReturn(Optional.of(mentor));
            given(userRepository.findById(MENTOR_USER_ID)).willReturn(Optional.of(mentorUser));

            // when & then
            assertThatThrownBy(() -> chatService.getOrCreateChatRoom(MENTORSHIP_ID, MENTOR_USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_NOT_AUTHOR.getMessage());
        }
    }

    // =========================================================
    // getMyChatRooms() - 내 채팅방 목록 조회
    // =========================================================
    @Nested
    @DisplayName("getMyChatRooms() - 내 채팅방 목록 조회")
    class GetMyChatRoomsTest {

        @Test
        @DisplayName("성공 - 채팅방 목록 반환 (읽지 않은 메시지 수 포함)")
        void getMyChatRooms_success() {
            // given
            List<ChatRoom> rooms = List.of(
                    createMockChatRoom(1L, MENTOR_USER_ID, MENTEE_USER_ID),
                    createMockChatRoom(2L, MENTOR_USER_ID, MENTEE_USER_ID)
            );
            List<Object[]> unreadCounts = List.of(
                    new Object[]{1L, 5L},
                    new Object[]{2L, 3L}
            );

            given(chatRoomRepository.findActiveRoomsByUserId(MENTOR_USER_ID)).willReturn(rooms);
            given(chatMessageRepository.countUnreadByRoomIds(anyList(), eq(MENTOR_USER_ID)))
                    .willReturn(unreadCounts);

            // when
            List<ChatRoomResponse> result = chatService.getMyChatRooms(MENTOR_USER_ID);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("성공 - 채팅방 없을 시 빈 리스트 반환")
        void getMyChatRooms_noRooms_returnsEmptyList() {
            // given
            given(chatRoomRepository.findActiveRoomsByUserId(MENTOR_USER_ID)).willReturn(List.of());

            // when
            List<ChatRoomResponse> result = chatService.getMyChatRooms(MENTOR_USER_ID);

            // then
            assertThat(result).isEmpty();
            verify(chatMessageRepository, never()).countUnreadByRoomIds(anyList(), anyLong());
        }
    }

    // =========================================================
    // getMessages() - 메시지 히스토리 조회
    // =========================================================
    @Nested
    @DisplayName("getMessages() - 메시지 히스토리 조회")
    class GetMessagesTest {

        @Test
        @DisplayName("성공 - 메시지 히스토리 페이징 조회")
        void getMessages_success() {
            // given
            ChatRoom room = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            List<ChatMessage> messages = List.of(
                    createMockChatMessage(1L, ROOM_ID, MENTOR_USER_ID, "메시지 1"),
                    createMockChatMessage(2L, ROOM_ID, MENTEE_USER_ID, "메시지 2")
            );
            Pageable pageable = PageRequest.of(0, 30);
            Page<ChatMessage> page = new PageImpl<>(messages, pageable, 2);

            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(ROOM_ID, pageable))
                    .willReturn(page);

            // when
            PageResponse<ChatMessageResponse> result = chatService.getMessages(ROOM_ID, MENTOR_USER_ID, pageable);

            // then
            assertThat(result.content()).hasSize(2);
        }

        @Test
        @DisplayName("실패 - 참여자가 아닐 시 예외")
        void getMessages_notParticipant_throwsException() {
            // given
            ChatRoom room = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(room.isParticipant(999L)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> chatService.getMessages(ROOM_ID, 999L, PageRequest.of(0, 30)))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_NOT_PARTICIPANT.getMessage());
        }
    }

    // =========================================================
    // saveMessage() - 메시지 저장
    // =========================================================
    @Nested
    @DisplayName("saveMessage() - 메시지 저장")
    class SaveMessageTest {

        @Test
        @DisplayName("성공 - TEXT 메시지 저장")
        void saveMessage_textMessage_success() {
            // given
            ChatRoom room = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            ChatMessageRequest request = new ChatMessageRequest("안녕하세요", MessageType.TEXT, null);
            ChatMessage savedMessage = createMockChatMessage(1L, ROOM_ID, MENTOR_USER_ID, "안녕하세요");

            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(savedMessage);

            // when
            ChatMessageResponse result = chatService.saveMessage(ROOM_ID, MENTOR_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
        }

        @Test
        @DisplayName("성공 - FILE 메시지 저장 (유효한 S3 URL)")
        void saveMessage_fileMessage_validS3Url_success() {
            // given
            ReflectionTestUtils.setField(chatService, "s3BucketName", S3_BUCKET);
            ReflectionTestUtils.setField(chatService, "s3Region", S3_REGION);

            ChatRoom room = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            String validS3Url = "https://hot6-novelcraft-chat.s3.ap-northeast-2.amazonaws.com/chat/file.pdf";
            ChatMessageRequest request = new ChatMessageRequest("파일입니다", MessageType.FILE, validS3Url);
            ChatMessage savedMessage = createMockChatMessage(1L, ROOM_ID, MENTOR_USER_ID, "파일입니다");

            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(savedMessage);

            // when
            ChatMessageResponse result = chatService.saveMessage(ROOM_ID, MENTOR_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
        }

        @Test
        @DisplayName("실패 - messageType이 null일 시 예외")
        void saveMessage_nullMessageType_throwsException() {
            // given
            ChatMessageRequest request = new ChatMessageRequest("내용", null, null);

            // when & then
            assertThatThrownBy(() -> chatService.saveMessage(ROOM_ID, MENTOR_USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_INVALID_MESSAGE.getMessage());
        }

        @Test
        @DisplayName("실패 - content가 null 또는 빈 문자열일 시 예외")
        void saveMessage_emptyContent_throwsException() {
            // given
            ChatMessageRequest request = new ChatMessageRequest("", MessageType.TEXT, null);

            // when & then
            assertThatThrownBy(() -> chatService.saveMessage(ROOM_ID, MENTOR_USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_INVALID_MESSAGE.getMessage());
        }

        @Test
        @DisplayName("실패 - FILE 타입인데 fileUrl이 없을 시 예외")
        void saveMessage_fileTypeMissingUrl_throwsException() {
            // given
            ChatMessageRequest request = new ChatMessageRequest("파일입니다", MessageType.FILE, null);

            // when & then
            assertThatThrownBy(() -> chatService.saveMessage(ROOM_ID, MENTOR_USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_INVALID_MESSAGE.getMessage());
        }

        @Test
        @DisplayName("실패 - FILE 타입인데 유효하지 않은 S3 URL일 시 예외")
        void saveMessage_fileTypeInvalidS3Url_throwsException() {
            // given
            ReflectionTestUtils.setField(chatService, "s3BucketName", S3_BUCKET);
            ReflectionTestUtils.setField(chatService, "s3Region", S3_REGION);

            String invalidUrl = "https://malicious-site.com/file.pdf";
            ChatMessageRequest request = new ChatMessageRequest("파일입니다", MessageType.FILE, invalidUrl);

            // when & then
            assertThatThrownBy(() -> chatService.saveMessage(ROOM_ID, MENTOR_USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_INVALID_MESSAGE.getMessage());
        }

        @Test
        @DisplayName("실패 - 이미 나간 사용자가 메시지 보낼 시 예외")
        void saveMessage_userHasLeft_throwsException() {
            // given
            ChatRoom room = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            ChatMessageRequest request = new ChatMessageRequest("안녕하세요", MessageType.TEXT, null);

            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(room.hasLeft(MENTOR_USER_ID)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> chatService.saveMessage(ROOM_ID, MENTOR_USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_NOT_PARTICIPANT.getMessage());
        }
    }

    // =========================================================
    // leaveChatRoom() - 채팅방 나가기
    // =========================================================
    @Nested
    @DisplayName("leaveChatRoom() - 채팅방 나가기")
    class LeaveChatRoomTest {

        @Test
        @DisplayName("성공 - 채팅방 나가기 (leftAt 기록)")
        void leaveChatRoom_success() {
            // given
            ChatRoom room = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

            // when
            chatService.leaveChatRoom(ROOM_ID, MENTOR_USER_ID);

            // then
            verify(room, times(1)).leave(MENTOR_USER_ID);
        }

        @Test
        @DisplayName("실패 - 이미 나간 사용자가 다시 나가려고 할 시 예외")
        void leaveChatRoom_alreadyLeft_throwsException() {
            // given
            ChatRoom room = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(room.hasLeft(MENTOR_USER_ID)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> chatService.leaveChatRoom(ROOM_ID, MENTOR_USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ChatExceptionEnum.ERR_NOT_PARTICIPANT.getMessage());
        }
    }

    // =========================================================
    // markMessagesAsRead() - 읽음 처리
    // =========================================================
    @Nested
    @DisplayName("markMessagesAsRead() - 읽음 처리")
    class MarkMessagesAsReadTest {

        @Test
        @DisplayName("성공 - 메시지 읽음 처리")
        void markMessagesAsRead_success() {
            // given
            ChatRoom room = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.markAllAsRead(ROOM_ID, MENTOR_USER_ID)).willReturn(5);

            // when
            chatService.markMessagesAsRead(ROOM_ID, MENTOR_USER_ID);

            // then
            verify(chatMessageRepository, times(1)).markAllAsRead(ROOM_ID, MENTOR_USER_ID);
        }

        @Test
        @DisplayName("성공 - 읽을 메시지가 없어도 정상 처리")
        void markMessagesAsRead_noUnreadMessages_success() {
            // given
            ChatRoom room = createMockChatRoom(ROOM_ID, MENTOR_USER_ID, MENTEE_USER_ID);
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(chatMessageRepository.markAllAsRead(ROOM_ID, MENTOR_USER_ID)).willReturn(0);

            // when
            chatService.markMessagesAsRead(ROOM_ID, MENTOR_USER_ID);

            // then
            verify(chatMessageRepository, times(1)).markAllAsRead(ROOM_ID, MENTOR_USER_ID);
        }
    }
}
