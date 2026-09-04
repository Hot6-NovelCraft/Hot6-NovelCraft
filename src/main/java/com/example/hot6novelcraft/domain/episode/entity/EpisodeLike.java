package com.example.hot6novelcraft.domain.episode.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "episode_likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "episode_id"}))
public class EpisodeLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long episodeId;

    @Builder
    public EpisodeLike(Long userId, Long episodeId) {
        this.userId = userId;
        this.episodeId = episodeId;
    }
}
