package com.example.hot6novelcraft.domain.chatroom.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_rooms")
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long mentorshipId;

    @Column(nullable = false)
    private Long menteeId;

    @Column(nullable = false)
    private Long mentorId;

    private LocalDateTime mentorLeftAt;   // 멘토가 나간 시각 (null = 아직 참여 중)
    private LocalDateTime menteeLeftAt;   // 멘티가 나간 시각 (null = 아직 참여 중)

    private ChatRoom(Long mentorshipId, Long mentorId, Long menteeId) {
        this.mentorshipId = mentorshipId;
        this.mentorId = mentorId;
        this.menteeId = menteeId;
    }

    public static ChatRoom create(Long mentorshipId, Long mentorId, Long menteeId) {
        return new ChatRoom(mentorshipId, mentorId, menteeId);
    }

    public boolean isParticipant(Long userId) {
        return mentorId.equals(userId) || menteeId.equals(userId);
    }

    /**
     * 해당 유저가 이미 나갔는지 여부
     */
    public boolean hasLeft(Long userId) {
        if (mentorId.equals(userId)) return mentorLeftAt != null;
        if (menteeId.equals(userId)) return menteeLeftAt != null;
        return false;
    }

    /**
     * 나가기 처리 — 해당 참여자의 leftAt 기록
     */
    public void leave(Long userId) {
        if (mentorId.equals(userId)) this.mentorLeftAt = LocalDateTime.now();
        else if (menteeId.equals(userId)) this.menteeLeftAt = LocalDateTime.now();
    }

    /**
     * 재입장 처리 — leftAt 초기화
     */
    public void rejoin(Long userId) {
        if (mentorId.equals(userId)) this.mentorLeftAt = null;
        else if (menteeId.equals(userId)) this.menteeLeftAt = null;
    }
}
