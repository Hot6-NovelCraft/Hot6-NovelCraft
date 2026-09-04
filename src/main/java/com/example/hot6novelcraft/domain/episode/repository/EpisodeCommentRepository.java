package com.example.hot6novelcraft.domain.episode.repository;

import com.example.hot6novelcraft.domain.episode.entity.EpisodeComment;
import org.springframework.data.jpa.repository.JpaRepository;


public interface EpisodeCommentRepository extends JpaRepository<EpisodeComment, Long>, CustomEpisodeCommentRepository {
}