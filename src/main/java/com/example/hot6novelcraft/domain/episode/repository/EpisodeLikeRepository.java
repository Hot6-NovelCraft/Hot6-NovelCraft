package com.example.hot6novelcraft.domain.episode.repository;

import com.example.hot6novelcraft.domain.episode.entity.EpisodeLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EpisodeLikeRepository extends JpaRepository<EpisodeLike, Long> {

    // 이미 좋아요 했는지 확인
    Optional<EpisodeLike> findByUserIdAndEpisodeId(Long userId, Long episodeId);
}