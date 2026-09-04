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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MentorshipRepository mentorshipRepository;
    private final MentorRepository mentorRepository;
    private final UserRepository userRepository;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String s3BucketName;

    @Value("${spring.cloud.aws.region.static}")
    private String s3Region;

    /**
     * 채팅방 생성 or 기존 채팅방 반환.
     * 동일 mentorshipId로 요청이 오면 기존 방을 반환한다.
     */
    @Transactional
    public ChatRoomResponse getOrCreateChatRoom(Long mentorshipId, Long userId) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ServiceErrorException(ChatExceptionEnum.ERR_MENTORSHIP_NOT_FOUND));

        // mentorship.getMentorId() = mentors 테이블의 PK → Mentor 엔티티로 실제 users.id 조회
        Mentor mentor = mentorRepository.findById(mentorship.getMentorId())
                .orElseThrow(() -> new ServiceErrorException(ChatExceptionEnum.ERR_MENTOR_NOT_FOUND));
        Long mentorUserId = mentor.getUserId();   // 실제 users.id
        Long menteeUserId = mentorship.getMenteeId(); // mentorships.mentee_id = users.id

        // 요청자가 멘토 또는 멘티 유저인지 확인
        if (!mentorUserId.equals(userId) && !menteeUserId.equals(userId)) {
            throw new ServiceErrorException(ChatExceptionEnum.ERR_NOT_PARTICIPANT);
        }

        // 채팅은 AUTHOR 간에만 허용
        validateAuthorRole(mentorUserId);
        validateAuthorRole(menteeUserId);

        return chatRoomRepository.findByMentorshipId(mentorshipId)
                .map(room -> {
                    // 이전에 나갔던 경우 재입장 처리 (leftAt 초기화)
                    if (room.hasLeft(userId)) room.rejoin(userId);
                    return ChatRoomResponse.from(room);
                })
                .orElseGet(() -> {
                    try {
                        ChatRoom room = chatRoomRepository.saveAndFlush(
                                ChatRoom.create(mentorshipId, mentorUserId, menteeUserId)
                        );
                        return ChatRoomResponse.from(room);
                    } catch (DataIntegrityViolationException e) {
                        // 동시 요청으로 unique 제약 위반 시 이미 생성된 방 반환
                        return ChatRoomResponse.from(
                                chatRoomRepository.findByMentorshipId(mentorshipId)
                                        .orElseThrow(() -> new ServiceErrorException(ChatExceptionEnum.ERR_CHATROOM_NOT_FOUND))
                        );
                    }
                });
    }

    /**
     * 내가 참여 중인 채팅방 목록 조회 (나가지 않은 방만, 읽지 않은 메시지 수 포함)
     */
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyChatRooms(Long userId) {
        List<ChatRoom> rooms = chatRoomRepository.findActiveRoomsByUserId(userId);
        if (rooms.isEmpty()) return List.of();

        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<Long, Long> unreadCounts = chatMessageRepository.countUnreadByRoomIds(roomIds, userId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return rooms.stream()
                .map(room -> ChatRoomResponse.from(room, unreadCounts.getOrDefault(room.getId(), 0L)))
                .toList();
    }

    /**
     * 채팅방 메시지 히스토리 조회 (최신순 페이징)
     */
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> getMessages(Long roomId, Long userId, Pageable pageable) {
        ChatRoom room = getChatRoomOrThrow(roomId);
        validateParticipant(room, userId);
        return PageResponse.register(
                chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable)
                        .map(ChatMessageResponse::from)
        );
    }

    /**
     * 메시지 저장 (WebSocket 수신 시 호출)
     */
    @Transactional
    public ChatMessageResponse saveMessage(Long roomId, Long senderId, ChatMessageRequest request) {
        // 메시지 타입 및 내용 검증
        if (request.messageType() == null) {
            throw new ServiceErrorException(ChatExceptionEnum.ERR_INVALID_MESSAGE);
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new ServiceErrorException(ChatExceptionEnum.ERR_INVALID_MESSAGE);
        }

        // FILE 타입일 경우 fileUrl 필수 및 출처 검증
        if (request.messageType() == MessageType.FILE) {
            if (request.fileUrl() == null || request.fileUrl().isBlank()) {
                throw new ServiceErrorException(ChatExceptionEnum.ERR_INVALID_MESSAGE);
            }
            // S3 버킷 URL만 허용 (링크 위·변조 방지)
            if (!isValidS3Url(request.fileUrl())) {
                throw new ServiceErrorException(ChatExceptionEnum.ERR_INVALID_MESSAGE);
            }
        }

        ChatRoom room = getChatRoomOrThrow(roomId);
        validateActiveParticipant(room, senderId);

        // messageType에 따라 적절한 팩토리 메서드 사용
        ChatMessage message = (request.messageType() == MessageType.FILE)
                ? chatMessageRepository.save(ChatMessage.createWithFile(roomId, senderId, request.content(), request.messageType(), request.fileUrl()))
                : chatMessageRepository.save(ChatMessage.create(roomId, senderId, request.content(), request.messageType()));

        return ChatMessageResponse.from(message);
    }

    /**
     * 채팅방 나가기 - 나간 시각만 기록, 방과 메시지는 상대방을 위해 유지
     */
    @Transactional
    public void leaveChatRoom(Long roomId, Long userId) {
        ChatRoom room = getChatRoomOrThrow(roomId);
        validateParticipant(room, userId);
        if (room.hasLeft(userId)) {
            throw new ServiceErrorException(ChatExceptionEnum.ERR_NOT_PARTICIPANT);
        }
        room.leave(userId);
    }

    /**
     * 채팅방 입장 시 상대방 메시지 읽음 처리
     */
    @Transactional
    public void markMessagesAsRead(Long roomId, Long userId) {
        ChatRoom room = getChatRoomOrThrow(roomId);
        validateActiveParticipant(room, userId);

        int updatedCount = chatMessageRepository.markAllAsRead(roomId, userId);

        if (updatedCount > 0) {
            log.info("[Chat] {} 개 메시지 읽음 처리 완료 roomId={} userId={}", updatedCount, roomId, userId);
        }
    }

    private ChatRoom getChatRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ServiceErrorException(ChatExceptionEnum.ERR_CHATROOM_NOT_FOUND));
    }

    private void validateParticipant(ChatRoom room, Long userId) {
        if (!room.isParticipant(userId)) {
            throw new ServiceErrorException(ChatExceptionEnum.ERR_NOT_PARTICIPANT);
        }
    }

    private void validateActiveParticipant(ChatRoom room, Long userId) {
        validateParticipant(room, userId);
        if (room.hasLeft(userId)) {
            throw new ServiceErrorException(ChatExceptionEnum.ERR_NOT_PARTICIPANT);
        }
    }

    private void validateAuthorRole(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceErrorException(ChatExceptionEnum.ERR_NOT_PARTICIPANT));
        if (user.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(ChatExceptionEnum.ERR_NOT_AUTHOR);
        }
    }

    /**
     * S3 버킷 URL 검증 - 링크 위·변조 방지
     */
    private boolean isValidS3Url(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return false;
        }

        // 허용되는 S3 URL 패턴:
        // https://hot6-novelcraft-chat.s3.ap-northeast-2.amazonaws.com/chat/...
        String expectedPrefix = String.format("https://%s.s3.%s.amazonaws.com/chat/",
                s3BucketName, s3Region);

        return fileUrl.startsWith(expectedPrefix);
    }
}
