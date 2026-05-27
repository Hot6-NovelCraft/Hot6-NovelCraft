package com.example.hot6novelcraft.domain.episode.repository;

import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeStatResponse;

import java.util.List;

public interface CustomEpisodeStatRepository {
    // 작가용 회차별 통계 조회(댓글,일별 회차 조회수, 구매수)
    List<EpisodeStatResponse> findEpisodeStatsByNovelId(Long novelId);
}