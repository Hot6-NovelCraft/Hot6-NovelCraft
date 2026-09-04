package com.example.hot6novelcraft.domain.event.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "event_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private EventParticipant(Long eventId, Long userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.joinedAt = LocalDateTime.now();
    }

    public static EventParticipant create(Long eventId, Long userId) {
        return new EventParticipant(eventId, userId);
    }
}