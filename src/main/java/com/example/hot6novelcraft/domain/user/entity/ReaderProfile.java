package com.example.hot6novelcraft.domain.user.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.user.entity.enums.ReadingGoal;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reader_profiles")
public class ReaderProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = true)
    private String preferredGenres;

    @Enumerated(EnumType.STRING)
    private ReadingGoal readingGoal;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PreRemove
    protected void preRemove() {
        deletedAt = LocalDateTime.now();
    }

    public static ReaderProfile register(Long userId, String preferredGenres, ReadingGoal readingGoal) {
        return ReaderProfile.builder()
                .userId(userId)
                .preferredGenres(preferredGenres)
                .readingGoal(readingGoal)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // 독자 프로필 수정
    public void readerUpdateProfile(String preferredGenres, ReadingGoal readingGoal) {
        if (preferredGenres != null) {
            this.preferredGenres = preferredGenres;
        }
        if (readingGoal != null) {
            this.readingGoal = readingGoal;
        }
        this.updatedAt = LocalDateTime.now();
    }
}
