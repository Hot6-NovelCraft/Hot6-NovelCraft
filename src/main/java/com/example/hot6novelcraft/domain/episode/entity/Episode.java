package com.example.hot6novelcraft.domain.episode.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "episodes",
        indexes = @Index(name = "idx_episode_novel_id", columnList = "novel_id"))
public class Episode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long novelId;

    @Column(nullable = false)
    private int episodeNumber;

    @Column(nullable = false, length = 20)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean isFree = false;

    @Column(nullable = false)
    private int pointPrice = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EpisodeStatus status;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private Long likeCount = 0L;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
        status = EpisodeStatus.DRAFT;
        likeCount = 0L;
        isDeleted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 회차 생성 (정적 팩토리)
    public static Episode createEpisode(Long novelId, int episodeNumber, String title,
                                        String content, boolean isFree, int pointPrice) {
        return Episode.builder()
                .novelId(novelId)
                .episodeNumber(episodeNumber)
                .title(title)
                .content(content)
                .isFree(isFree)
                .pointPrice(pointPrice)
                .build();
    }

    // 회차 수정
    public void update(String title, String content) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
    }

    // 발행
    public void publish() {
        this.status = EpisodeStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    // 삭제 (소프트 딜리트)
    public void delete() {
        this.isDeleted = true;
        deletedAt = LocalDateTime.now();
    }
}