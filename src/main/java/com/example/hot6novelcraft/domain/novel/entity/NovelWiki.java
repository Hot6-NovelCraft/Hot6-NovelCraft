package com.example.hot6novelcraft.domain.novel.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.novel.entity.enums.WikiCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "novel_wiki",
        indexes = @Index(name = "idx_wiki_novel_id", columnList = "novel_id"))
public class NovelWiki extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long novelId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WikiCategory category;

    @Column(nullable = false, length = 20)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 설정집 생성
    public static NovelWiki createWiki(Long novelId, WikiCategory category,
                                       String title, String content) {
        return NovelWiki.builder()
                .novelId(novelId)
                .category(category)
                .title(title)
                .content(content)
                .build();
    }

    @Builder
    public NovelWiki(Long novelId, WikiCategory category, String title, String content) {
        this.novelId = novelId;
        this.category = category;
        this.title = title;
        this.content = content;
    }
}