package com.example.hot6novelcraft.domain.reviewai.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.reviewai.dto.cache.AiReviewJob;
import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewJobResponse;
import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewResponse;
import com.example.hot6novelcraft.domain.reviewai.service.AiReviewService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/author")
public class AiReviewController {

    private final AiReviewService aiReviewService;

    /**
     * AI 리뷰 받기 v1
     * - episodeId의 본문을 기반으로 AI가 평점 + 독자 댓글 생성
     * 정은식
     */
    @PostMapping("/v1/episodes/{episodeId}/ai-review")
    public ResponseEntity<BaseResponse<AiReviewResponse>> getAiReview(
            @PathVariable Long episodeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        AiReviewResponse response = aiReviewService.getReview(episodeId, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "AI 리뷰 조회 성공", response)
        );
    }

    /**
     * AI 리뷰 받기 v2 (비동기, Kafka)
     * - 즉시 jobId 반환, 실제 처리는 백그라운드에서 진행
     * - 결과는 GET /v2/jobs/{jobId} 로 폴링 조회
     * 정은식
     */
    @PostMapping("/v2/episodes/{episodeId}/ai-review")
    public ResponseEntity<BaseResponse<AiReviewJobResponse>> requestAiReviewAsync(
            @PathVariable Long episodeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        AiReviewJobResponse response = aiReviewService.requestReviewAsync(episodeId, userDetails);

        return ResponseEntity.accepted().body(
                BaseResponse.success("202", "AI 리뷰 요청이 접수되었습니다", response)
        );
    }

    /**
     * AI 리뷰 Job 상태 조회 (v2 폴링용)
     * - PROCESSING: 아직 처리중
     * - COMPLETED: 결과 포함
     * - FAILED: 에러 메시지 포함
     * 정은식
     */
    @GetMapping("/v2/jobs/{jobId}")
    public ResponseEntity<BaseResponse<AiReviewJob>> getJobStatus(
            @PathVariable String jobId
    ) {
        AiReviewJob job = aiReviewService.getJobStatus(jobId);

        return ResponseEntity.ok(
                BaseResponse.success("200", "Job 상태 조회 성공", job)
        );
    }
}