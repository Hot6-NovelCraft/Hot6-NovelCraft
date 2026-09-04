package com.example.hot6novelcraft.domain.reviewai.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AiReviewMessage(
        String jobId,
        Long episodeId,
        Long userId,
        String title,
        String content
) {

    @JsonCreator
    public AiReviewMessage(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("episodeId") Long episodeId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content
    ) {
        this.jobId = jobId;
        this.episodeId = episodeId;
        this.userId = userId;
        this.title = title;
        this.content = content;
    }
}