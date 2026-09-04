package com.example.hot6novelcraft.domain.chatmessage.repository;

import com.example.hot6novelcraft.domain.chatmessage.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    // 상대방이 보낸 읽지 않은 메시지 수
    long countByRoomIdAndSenderIdNotAndIsReadFalse(Long roomId, Long senderId);

    // 여러 방의 읽지 않은 메시지 수 배치 조회 (N+1 방지)
    @Query("SELECT m.roomId, COUNT(m) FROM ChatMessage m WHERE m.roomId IN :roomIds AND m.senderId != :userId AND m.isRead = false GROUP BY m.roomId")
    List<Object[]> countUnreadByRoomIds(@Param("roomIds") List<Long> roomIds, @Param("userId") Long userId);

    // 상대방이 보낸 메시지를 읽음 처리
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.roomId = :roomId AND m.senderId != :userId AND m.isRead = false")
    int markAllAsRead(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
