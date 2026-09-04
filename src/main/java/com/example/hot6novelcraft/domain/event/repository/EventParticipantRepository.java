package com.example.hot6novelcraft.domain.event.repository;

import com.example.hot6novelcraft.domain.event.entity.EventParticipant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    long countByEventId(Long eventId);

    Page<EventParticipant> findAllByEventId(Long eventId, Pageable pageable);
}