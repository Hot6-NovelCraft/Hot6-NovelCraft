package com.example.hot6novelcraft.domain.coverai.dto.response;

import com.example.hot6novelcraft.domain.coverai.entity.CoverJob;
import com.example.hot6novelcraft.domain.coverai.entity.enums.CoverJobStatus;

public record CoverJobResponse(
        String jobId,
        Long novelId,
        CoverJobStatus status,
        String coverImageUrl,
        String errorMessage
) {
    public static CoverJobResponse from(CoverJob job) {
        return new CoverJobResponse(
                job.getJobId(),
                job.getNovelId(),
                job.getStatus(),
                job.getCoverImageUrl(),
                job.getErrorMessage()
        );
    }
}