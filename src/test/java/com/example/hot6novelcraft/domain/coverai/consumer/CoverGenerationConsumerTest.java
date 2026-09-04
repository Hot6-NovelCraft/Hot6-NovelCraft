package com.example.hot6novelcraft.domain.coverai.consumer;

import com.example.hot6novelcraft.domain.coverai.client.GeminiClient;
import com.example.hot6novelcraft.domain.coverai.dto.event.CoverGenerationEvent;
import com.example.hot6novelcraft.domain.coverai.entity.CoverJob;
import com.example.hot6novelcraft.domain.coverai.entity.enums.CoverJobStatus;
import com.example.hot6novelcraft.domain.coverai.repository.CoverJobRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoverGenerationConsumerTest {

    @InjectMocks
    private CoverGenerationConsumer consumer;

    @Mock private CoverJobRepository coverJobRepository;
    @Mock private NovelRepository novelRepository;
    @Mock private UserRepository userRepository;
    @Mock private GeminiClient geminiClient;
    @Mock private S3Client s3Client;
    @Mock private PointService pointService;
    @Mock private PointHistoryRepository pointHistoryRepository;

    private CoverJob job;
    private Novel novel;
    private User author;
    private CoverGenerationEvent event;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(consumer, "region", "ap-northeast-2");

        job = CoverJob.create("test-job-id", 1L, 1L);

        author = User.register("author@test.com", "password", "테스트작가", "01012345678", null, UserRole.AUTHOR);
        ReflectionTestUtils.setField(author, "id", 1L);

        novel = Novel.builder()
                .authorId(1L)
                .title("달빛 아래 검은 장미")
                .description("어둠의 마법사와 왕국의 기사가 금지된 사랑을 나누는 이야기")
                .genre("판타지")
                .tags("판타지,로맨스")
                .status(NovelStatus.PENDING)
                .viewCount(0L)
                .bookmarkCount(0)
                .build();
        ReflectionTestUtils.setField(novel, "id", 1L);
        ReflectionTestUtils.setField(novel, "isDeleted", false);

        event = new CoverGenerationEvent("test-job-id", 1L, 1L);
    }

    @Test
    @DisplayName("표지 생성 성공 - COMPLETED 상태 및 포인트 차감")
    void consume_success() {
        // given
        given(coverJobRepository.findByJobId("test-job-id")).willReturn(Optional.of(job));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(geminiClient.generateImage(any())).willReturn(new byte[]{1, 2, 3});
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(pointHistoryRepository.save(any(PointHistory.class))).willReturn(null);

        // when
        consumer.consume(event);

        // then
        assertThat(job.getStatus()).isEqualTo(CoverJobStatus.COMPLETED);
        assertThat(job.getCoverImageUrl()).contains("test-bucket");
        assertThat(job.getErrorMessage()).isNull();

        verify(pointService).deduct(1L, 300L);
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("이미 처리된 Job 중복 이벤트 스킵")
    void consume_duplicateEvent_skip() {
        // given
        CoverJob completedJob = CoverJob.create("test-job-id", 1L, 1L);
        completedJob.complete("https://s3.amazonaws.com/test.png");
        given(coverJobRepository.findByJobId("test-job-id")).willReturn(Optional.of(completedJob));

        // when
        consumer.consume(event);

        // then - Gemini 호출 안 됨
        verify(geminiClient, never()).generateImage(any());
        verify(pointService, never()).deduct(any(), any());
    }

    @Test
    @DisplayName("Gemini 실패 시 차감 전이면 환불 안 함")
    void consume_geminiFailure_noRefundIfNotDeducted() {
        // given
        given(coverJobRepository.findByJobId("test-job-id")).willReturn(Optional.of(job));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(geminiClient.generateImage(any())).willThrow(new RuntimeException("Gemini 실패"));

        // when
        consumer.consume(event);

        // then
        assertThat(job.getStatus()).isEqualTo(CoverJobStatus.FAILED);
        verify(pointService, never()).deduct(any(), any());
        verify(pointService, never()).charge(any(), any()); // 차감 안 됐으니 환불도 없음
    }

    @Test
    @DisplayName("S3 업로드 3회 실패 - FAILED 상태 및 포인트 환불 없음")
    void consume_s3Failure_noRefund() {
        // given
        given(coverJobRepository.findByJobId("test-job-id")).willReturn(Optional.of(job));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(geminiClient.generateImage(any())).willReturn(new byte[]{1, 2, 3});
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(new RuntimeException("S3 연결 실패"));

        // when
        consumer.consume(event);

        // then
        assertThat(job.getStatus()).isEqualTo(CoverJobStatus.FAILED);
        verify(pointService, never()).deduct(any(), any());
        verify(pointService, never()).charge(any(), any()); // 차감 전 실패라 환불 없음
    }

    @Test
    @DisplayName("포인트 차감 후 DB 저장 실패 - FAILED 상태 및 포인트 환불")
    void consume_afterDeductFailure_refund() {
        // given
        given(coverJobRepository.findByJobId("test-job-id")).willReturn(Optional.of(job));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(geminiClient.generateImage(any())).willReturn(new byte[]{1, 2, 3});
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(pointHistoryRepository.save(any(PointHistory.class)))
                .willThrow(new RuntimeException("DB 저장 실패")); // 차감 후 실패

        // when
        consumer.consume(event);

        // then
        assertThat(job.getStatus()).isEqualTo(CoverJobStatus.FAILED);
        verify(pointService).deduct(1L, 300L); // 차감은 됨
        verify(pointService).charge(1L, 300L); // 환불도 됨
    }

    @Test
    @DisplayName("Gemini 1회 실패 후 재시도 성공")
    void consume_geminiRetrySuccess() {
        // given
        given(coverJobRepository.findByJobId("test-job-id")).willReturn(Optional.of(job));
        given(novelRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(novel));
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(geminiClient.generateImage(any()))
                .willThrow(new RuntimeException("일시적 오류"))
                .willReturn(new byte[]{1, 2, 3});
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(pointHistoryRepository.save(any())).willReturn(null);

        // when
        consumer.consume(event);

        // then
        assertThat(job.getStatus()).isEqualTo(CoverJobStatus.COMPLETED);
        verify(geminiClient, times(2)).generateImage(any());
        verify(pointService).deduct(1L, 300L);
    }

    @Test
    @DisplayName("존재하지 않는 jobId - IllegalStateException 발생")
    void consume_jobNotFound() {
        // given
        given(coverJobRepository.findByJobId("invalid-id")).willReturn(Optional.empty());
        CoverGenerationEvent invalidEvent = new CoverGenerationEvent("invalid-id", 1L, 1L);

        // when & then
        assertThatThrownBy(() -> consumer.consume(invalidEvent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Job not found");
    }
}