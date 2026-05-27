package com.example.hot6novelcraft.domain.chatroom.repository;

import com.example.hot6novelcraft.domain.chatroom.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByMentorshipId(Long mentorshipId);

    /**
     * 해당 유저가 나가지 않은 채팅방만 조회
     */
    @Query("""
            SELECT r FROM ChatRoom r
            WHERE (r.mentorId = :userId AND r.mentorLeftAt IS NULL)
               OR (r.menteeId = :userId AND r.menteeLeftAt IS NULL)
            """)
    List<ChatRoom> findActiveRoomsByUserId(@Param("userId") Long userId);
}
