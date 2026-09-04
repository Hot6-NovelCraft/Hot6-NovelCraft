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
@Table(
        name = "comments",
        indexes = {
                @Index(name = "idx_comments_episode", columnList = "episodeId"),
                @Index(name = "idx_comments_user", columnList = "userId")
        }
)
public class EpisodeComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long episodeId;

    @Column(nullable = false, length = 500)
    private String content;


    @Builder
    public EpisodeComment(Long userId, Long episodeId, String content) {
        this.userId = userId;
        this.episodeId = episodeId;
        this.content = content;
    }
}
