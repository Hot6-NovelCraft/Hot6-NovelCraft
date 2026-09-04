package com.example.hot6novelcraft.domain.event.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Long rewardPoints;

    private Long maxParticipants;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime endedAt;

    private Event(Long createdBy, String title, String description,
                  Long rewardPoints, Long maxParticipants,
                  LocalDateTime startedAt, LocalDateTime endedAt) {
        this.createdBy = createdBy;
        this.title = title;
        this.description = description;
        this.rewardPoints = rewardPoints;
        this.maxParticipants = maxParticipants;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public static Event create(Long createdBy, String title, String description,
                               Long rewardPoints, Long maxParticipants,
                               LocalDateTime startedAt, LocalDateTime endedAt) {
        return new Event(createdBy, title, description, rewardPoints, maxParticipants, startedAt, endedAt);
    }

    public boolean isOngoing() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startedAt) && !now.isAfter(endedAt);
    }

    public boolean isEnded() {
        return LocalDateTime.now().isAfter(endedAt);
    }
}