package com.example.hot6novelcraft.domain.library.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.library.entity.enums.LibraryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "library",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "novel_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Library extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long novelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LibraryType libraryType;

    @Column(nullable = false, length = 200)
    private String novelTitle;

    @Column(nullable = false, length = 100)
    private String authorNickname;

    @Column(length = 500)
    private String coverImageUrl;

    @Column(length = 200)
    private String lastReadEpisodeTitle;

    private Library(Long userId, Long novelId, LibraryType libraryType,
                    String novelTitle, String authorNickname, String coverImageUrl) {
        this.userId = userId;
        this.novelId = novelId;
        this.libraryType = libraryType;
        this.novelTitle = novelTitle;
        this.authorNickname = authorNickname;
        this.coverImageUrl = coverImageUrl;
    }

    public static Library create(Long userId, Long novelId, LibraryType libraryType,
                                 String novelTitle, String authorNickname, String coverImageUrl) {
        return new Library(userId, novelId, libraryType, novelTitle, authorNickname, coverImageUrl);
    }

    public void changeType(LibraryType libraryType) {
        this.libraryType = libraryType;
    }

    public void updateLastReadEpisode(String episodeTitle) {
        this.lastReadEpisodeTitle = episodeTitle;
    }
}
