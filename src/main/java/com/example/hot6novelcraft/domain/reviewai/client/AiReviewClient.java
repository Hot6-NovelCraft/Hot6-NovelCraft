package com.example.hot6novelcraft.domain.reviewai.client;

import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewResponse;

public interface AiReviewClient {

    // 회차 본문 기반 AI 리뷰 생성
    AiReviewResponse generate(Long episodeId, String title, String content);
}