package com.example.hot6novelcraft.domain.coverai.consumer;

import com.example.hot6novelcraft.domain.coverai.client.GeminiClient;
import com.example.hot6novelcraft.domain.coverai.dto.event.CoverGenerationEvent;
import com.example.hot6novelcraft.domain.coverai.entity.CoverJob;
import com.example.hot6novelcraft.domain.coverai.entity.enums.CoverJobStatus;
import com.example.hot6novelcraft.domain.coverai.repository.CoverJobRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoverGenerationConsumer {

    private final CoverJobRepository coverJobRepository;
    private final NovelRepository novelRepository;
    private final UserRepository userRepository;
    private final GeminiClient geminiClient;
    private final S3Client s3Client;
    private final PointService pointService;
    private final PointHistoryRepository pointHistoryRepository;

    private static final Long COVER_COST = 300L;
    private static final int MAX_RETRY = 3;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    @KafkaListener(
            topics = "${cover.kafka.topic}",
            containerFactory = "coverKafkaListenerContainerFactory"
    )
    public void consume(CoverGenerationEvent event) {
        log.info("[Cover] 표지 생성 시작 jobId={} novelId={}", event.jobId(), event.novelId());

        CoverJob job = coverJobRepository.findByJobId(event.jobId())
                .orElseThrow(() -> new IllegalStateException("Job not found: " + event.jobId()));

        // 1번 - 중복 이벤트 방어
        if (job.getStatus() != CoverJobStatus.PENDING) {
            log.warn("[Cover] 이미 처리된 Job 스킵 jobId={} status={}", event.jobId(), job.getStatus());
            return;
        }

        job.processing();
        coverJobRepository.save(job);

        boolean isDeducted = false; // 2번 - 차감 여부 플래그

        try {
            // 1. 소설/유저 정보 조회
            Novel novel = novelRepository.findByIdAndIsDeletedFalse(event.novelId())
                    .orElseThrow(() -> new IllegalStateException("Novel not found: " + event.novelId()));
            User user = userRepository.findById(event.userId())
                    .orElseThrow(() -> new IllegalStateException("User not found: " + event.userId()));

            // 2. 프롬프트 생성 → Gemini 호출 (재시도)
            byte[] imageBytes = generateImageWithRetry(novel, user.getNickname(), event.jobId());

            // 3. S3 업로드 (재시도)
            String s3Url = uploadToS3WithRetry(imageBytes, event.novelId(), event.jobId());

            // 4. 포인트 차감 및 이력 저장
            pointService.deduct(event.userId(), COVER_COST);
            isDeducted = true; // 차감 완료 플래그
            pointHistoryRepository.save(
                    PointHistory.create(event.userId(), event.novelId(), null, COVER_COST,
                            PointHistoryType.AI_COVER, "AI 소설 표지 생성")
            );

            // 5. 완료 처리
            job.complete(s3Url);
            coverJobRepository.save(job);
            log.info("[Cover] 표지 생성 완료 jobId={} url={}", event.jobId(), s3Url);

        } catch (Exception e) {
            log.error("[Cover] 표지 생성 실패 jobId={}", event.jobId(), e);
            job.fail(e.getMessage());
            coverJobRepository.save(job);

            // 2번 - 차감된 경우에만 환불
            if (isDeducted) {
                try {
                    pointService.charge(event.userId(), COVER_COST);
                    pointHistoryRepository.save(
                            PointHistory.create(event.userId(), event.novelId(), null, COVER_COST,
                                    PointHistoryType.CHARGE, "AI 표지 생성 실패 환불")
                    );
                    log.info("[Cover] 포인트 환불 완료 userId={}", event.userId());
                } catch (Exception refundEx) {
                    log.error("[Cover] 포인트 환불 실패 userId={}", event.userId(), refundEx);
                }
            }
        }
    }

    private byte[] generateImageWithRetry(Novel novel, String authorName, String jobId) {
        String prompt = buildPrompt(novel, authorName);
        Exception lastException = null;

        for (int i = 1; i <= MAX_RETRY; i++) {
            try {
                return geminiClient.generateImage(prompt);
            } catch (Exception e) {
                lastException = e;
                log.warn("[Cover] Gemini 호출 실패 {}/{}회 jobId={}", i, MAX_RETRY, jobId, e);
            }
        }
        throw new RuntimeException("Gemini 호출 최대 재시도 초과", lastException);
    }

    private String uploadToS3WithRetry(byte[] imageBytes, Long novelId, String jobId) {
        Exception lastException = null;

        for (int i = 1; i <= MAX_RETRY; i++) {
            try {
                String s3Key = "covers/" + novelId + "/" + UUID.randomUUID() + ".png";
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType("image/png")
                        .contentLength((long) imageBytes.length)
                        .build();
                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
                return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, s3Key);
            } catch (Exception e) {
                lastException = e;
                log.warn("[Cover] S3 업로드 실패 {}/{}회 jobId={}", i, MAX_RETRY, jobId, e);
            }
        }
        throw new RuntimeException("S3 업로드 최대 재시도 초과", lastException);
    }

    private String buildPrompt(Novel novel, String authorName) {
        return String.format(
                "Create a professional Korean novel cover image. " +
                        "The title '%s' must be written clearly and legibly at the top in large, stylish Korean typography. " +
                        "Below the title, write the author name '%s 지음' in smaller Korean text. " +
                        "Genre: %s. Story summary: %s. " +
                        "Style: cinematic, high quality book cover art.",
                novel.getTitle(),
                authorName,
                novel.getGenre(),
                novel.getDescription()
        );
    }
}