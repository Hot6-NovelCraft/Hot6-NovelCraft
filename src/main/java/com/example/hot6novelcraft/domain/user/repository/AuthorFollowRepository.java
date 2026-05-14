package com.example.hot6novelcraft.domain.user.repository;

import com.example.hot6novelcraft.domain.user.entity.AuthorFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthorFollowRepository extends JpaRepository<AuthorFollow, Long> {

    // 이미 팔로우 했는지 검증
    Optional<AuthorFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Query("SELECT af.followerId FROM AuthorFollow af WHERE af.followingId = :authorId")
    List<Long> findFollowerIdsByFollowingId(@Param("authorId") Long authorId);

    long countByFollowingId(Long followingId);
}
