package com.example.hot6novelcraft.domain.episode.repository;

import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeCommentListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomEpisodeCommentRepository {

    // 댓글 목록 조회 (닉네임 포함)
    Page<EpisodeCommentListResponse> findCommentList(Long episodeId, Pageable pageable);
}