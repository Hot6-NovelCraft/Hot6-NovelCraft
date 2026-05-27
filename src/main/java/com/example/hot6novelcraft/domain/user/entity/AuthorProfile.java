package com.example.hot6novelcraft.domain.user.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "author_profiles")
public class AuthorProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 500)
    private String bio;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CareerLevel careerLevel;

    @Column(nullable = false)
    private String mainGenre;

    @Column(length = 500)
    private String instagramLinks;

    @Column(length = 500)
    private String xLinks;

    @Column(length = 500)
    private String blogLinks;

    @Column(nullable = false)
    private boolean allowMenteeRequest;

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

    public static AuthorProfile register(Long userId, String bio, CareerLevel careerLevel, String mainGenre, String instagramLinks, String xLinks, String blogLinks, boolean allowMenteeRequest) {
        return AuthorProfile.builder()
                .userId(userId)
                .bio(bio)
                .careerLevel(careerLevel)
                .mainGenre(mainGenre)
                .instagramLinks(instagramLinks)
                .xLinks(xLinks)
                .blogLinks(blogLinks)
                .allowMenteeRequest(allowMenteeRequest)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // 작가 프로필 수정
    public void authorUpdateProfile(String mainGenre, String bio, String instagramLinks, String xLinks, String blogLinks, boolean allowMenteeRequest) {
        if (mainGenre != null) {
            this.mainGenre = mainGenre;
        }
        if (bio != null) {
            this.bio = bio;
        }
        if (instagramLinks != null) {
            this.instagramLinks = instagramLinks;
        }
        if (xLinks != null) {
            this.xLinks = xLinks;
        }
        if (blogLinks != null) {
            this.blogLinks = blogLinks;
        }
        this.allowMenteeRequest = allowMenteeRequest;
        this.updatedAt = LocalDateTime.now();
    }
}
