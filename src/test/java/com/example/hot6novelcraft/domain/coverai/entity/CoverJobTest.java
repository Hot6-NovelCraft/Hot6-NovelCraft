package com.example.hot6novelcraft.domain.coverai.entity;

import com.example.hot6novelcraft.domain.coverai.entity.enums.CoverJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoverJobTest {

    private CoverJob job;

    @BeforeEach
    void setUp() {
        job = CoverJob.create("test-job-id", 1L, 1L);
    }

    @Test
    @DisplayName("CoverJob 생성 시 PENDING 상태")
    void create_initialStatus() {
        assertThat(job.getJobId()).isEqualTo("test-job-id");
        assertThat(job.getNovelId()).isEqualTo(1L);
        assertThat(job.getUserId()).isEqualTo(1L);
        assertThat(job.getStatus()).isEqualTo(CoverJobStatus.PENDING);
        assertThat(job.getCoverImageUrl()).isNull();
        assertThat(job.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("processing() 호출 시 PROCESSING 상태로 변경")
    void processing_statusChange() {
        job.processing();

        assertThat(job.getStatus()).isEqualTo(CoverJobStatus.PROCESSING);
    }

    @Test
    @DisplayName("complete() 호출 시 COMPLETED 상태 및 URL 저장")
    void complete_statusAndUrl() {
        String s3Url = "https://s3.amazonaws.com/covers/1/test.png";

        job.complete(s3Url);

        assertThat(job.getStatus()).isEqualTo(CoverJobStatus.COMPLETED);
        assertThat(job.getCoverImageUrl()).isEqualTo(s3Url);
    }

    @Test
    @DisplayName("fail() 호출 시 FAILED 상태 및 에러 메시지 저장")
    void fail_statusAndErrorMessage() {
        String errorMessage = "Gemini 호출 최대 재시도 초과";

        job.fail(errorMessage);

        assertThat(job.getStatus()).isEqualTo(CoverJobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo(errorMessage);
    }
}