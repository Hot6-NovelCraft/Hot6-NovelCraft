package com.example.hot6novelcraft.domain.reviewai.dto.cache;

import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewResponse;
import com.example.hot6novelcraft.domain.reviewai.entity.enums.AiReviewJobStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record AiReviewJob(
        String jobId,
        Long episodeId,
        Long userId,
        AiReviewJobStatus status,
        AiReviewResponse result,    // COMPLETED 일 때만 존재
        String errorMessage,         // FAILED 일 때만 존재
        LocalDateTime createdAt,
        LocalDateTime completedAt    // 완료/실패 시점
) {

    @JsonCreator
    public AiReviewJob(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("episodeId") Long episodeId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("status") AiReviewJobStatus status,
            @JsonProperty("result") AiReviewResponse result,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("createdAt") LocalDateTime createdAt,
            @JsonProperty("completedAt") LocalDateTime completedAt
    ) {
        this.jobId = jobId;
        this.episodeId = episodeId;
        this.userId = userId;
        this.status = status;
        this.result = result;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }


    // 신규 Job 생성 (PROCESSING 상태)
    public static AiReviewJob create(String jobId, Long episodeId, Long userId) {
        return new AiReviewJob(
                jobId,
                episodeId,
                userId,
                AiReviewJobStatus.PROCESSING,
                null,
                null,
                LocalDateTime.now(),
                null
        );
    }

    // 완료 상태로 전환
    public AiReviewJob completed(AiReviewResponse result) {
        return new AiReviewJob(
                this.jobId,
                this.episodeId,
                this.userId,
                AiReviewJobStatus.COMPLETED,
                result,
                null,
                this.createdAt,
                LocalDateTime.now()
        );
    }

    // 실패 상태로 전환
    public AiReviewJob failed(String errorMessage) {
        return new AiReviewJob(
                this.jobId,
                this.episodeId,
                this.userId,
                AiReviewJobStatus.FAILED,
                null,
                errorMessage,
                this.createdAt,
                LocalDateTime.now()
        );
    }
}