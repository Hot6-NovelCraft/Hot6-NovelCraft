package com.example.hot6novelcraft.domain.reviewai.dto.response;

import java.util.List;


// AI 리뷰 응답 DTO (작가에게 반환)
public record AiReviewResponse(
        Long episodeId,
        List<AiCommentResponse> comments
) {


    // AI 댓글 + 댓글 작성자별 평점
    public record AiCommentResponse(
            String nickname,
            String content,
            double rating
    ) {
    }
}