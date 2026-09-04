package com.example.hot6novelcraft.domain.reviewai.dto.response;

import com.example.hot6novelcraft.domain.reviewai.entity.enums.AiReviewJobStatus;


public record AiReviewJobResponse(
        String jobId,
        AiReviewJobStatus status,
        String message
) {
    public static AiReviewJobResponse processing(String jobId) {
        return new AiReviewJobResponse(
                jobId,
                AiReviewJobStatus.PROCESSING,
                "AI 리뷰 생성 중입니다. jobId로 결과를 조회해주세요."
        );
    }
}