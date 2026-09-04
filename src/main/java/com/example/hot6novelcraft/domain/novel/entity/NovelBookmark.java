package com.example.hot6novelcraft.domain.novel.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "novel_bookmarks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "novel_id"}))
public class NovelBookmark extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long novelId;

    @Builder
    public NovelBookmark(Long userId, Long novelId) {
        this.userId = userId;
        this.novelId = novelId;
    }
}