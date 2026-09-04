package com.example.hot6novelcraft.domain.user.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "author_follows",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"}))
public class AuthorFollow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long followerId;

    @Column(nullable = false)
    private Long followingId;

    @Builder
    public AuthorFollow(Long followerId, Long followingId) {
        this.followerId = followerId;
        this.followingId = followingId;
    }
}