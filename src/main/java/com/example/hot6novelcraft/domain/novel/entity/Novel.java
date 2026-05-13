package com.example.hot6novelcraft.domain.novel.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "novels",
        indexes = {
                @Index(name = "idx_novel_genre", columnList = "genre"),
                @Index(name = "idx_novel_status", columnList = "status"),

                // 대시보드 통계 및 상태 조회용
                @Index(name = "idx_novel_status_deleted", columnList = "is_deleted, status"),

                // 신작 소설 조회용
                @Index(name = "idx_novel_new_list", columnList = "is_deleted, status, genre, created_at")
        })
public class Novel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(length = 500)
    private String coverImageUrl;

    @Column(nullable = false, length = 50)
    private String genre;

    @Column(nullable = false, length = 500)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NovelStatus status;

    @Column(nullable = false)
    private Long viewCount = 0L;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private int bookmarkCount = 0;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
        status = NovelStatus.PENDING;
        viewCount = 0L;
        bookmarkCount = 0;
        isDeleted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 소설 등록
    public static Novel createNovel(Long authorId, String title, String description, String genre, String tags) {
        return Novel.builder()
                .authorId(authorId)
                .title(title)
                .description(description)
                .genre(genre)
                .tags(tags)
                .build();
    }

    // 소설 수정
    public void update(String title, String description, String genre, String tags) {
        this.title = title;
        this.description = description;
        this.genre = genre;
        this.tags = tags;
    }

    // 소설 삭제 (소프트 딜리트)
    public void delete() {
        if (!this.isDeleted) {  // 이미 삭제된 경우 실행 안 함
            this.isDeleted = true;
            this.deletedAt = LocalDateTime.now();
        }
    }

    // 소설 상태 변경 (연재중)
    public void changeStatus(NovelStatus status) {
        this.status = status;
    }

    // 커버 이미지 URL 업데이트
    public void updateCoverImage(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }
}
