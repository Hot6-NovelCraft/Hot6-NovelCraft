package com.example.hot6novelcraft.domain.event.repository;

import com.example.hot6novelcraft.domain.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface EventRepository extends JpaRepository<Event, Long> {

    // 진행 중 필터
    @Query("SELECT e FROM Event e WHERE :now BETWEEN e.startedAt AND e.endedAt")
    Page<Event> findAllOngoing(@Param("now") LocalDateTime now, Pageable pageable);

    // 종료 필터
    @Query("SELECT e FROM Event e WHERE e.endedAt < :now")
    Page<Event> findAllEnded(@Param("now") LocalDateTime now, Pageable pageable);

    // 전체
    Page<Event> findAll(Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.startedAt > :now")
    Page<Event> findAllUpcoming(@Param("now") LocalDateTime now, Pageable pageable);
}
