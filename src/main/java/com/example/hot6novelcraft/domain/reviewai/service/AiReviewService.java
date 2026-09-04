package com.example.hot6novelcraft.domain.reviewai.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EpisodeExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.example.hot6novelcraft.domain.reviewai.client.AiReviewClient;
import com.example.hot6novelcraft.domain.reviewai.dto.cache.AiReviewJob;
import com.example.hot6novelcraft.domain.reviewai.dto.event.AiReviewMessage;
import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewJobResponse;
import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewResponse;
import com.example.hot6novelcraft.domain.reviewai.entity.enums.AiReviewJobStatus;
import com.example.hot6novelcraft.domain.reviewai.producer.AiReviewProducer;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final EpisodeRepository episodeRepository;
    private final NovelRepository novelRepository;
    private final AiReviewClient aiReviewClient;
    private final PointService pointService;

    private final AiReviewProducer aiReviewProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static final String AI_REVIEW_LOCK_KEY = "lock:ai-review:";

    private static final Long AI_REVIEW_COST = 200L;
    private static final String JOB_KEY_PREFIX = "ai_review:job:";
    private static final Duration JOB_TTL = Duration.ofHours(1);

    // AI 리뷰 기능 (v1)
    @Transactional
    public AiReviewResponse getReview(Long episodeId, UserDetailsImpl userDetails) {

        Long userId = userDetails.getUser().getId();

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 회차 조회
        Episode episode = findEpisodeById(episodeId);

        // 본인 소설 확인
        validateOwnership(episode.getNovelId(), userId);

        // 발행 전(DRAFT) 회차만 AI 리뷰 가능
        if (episode.getStatus() != EpisodeStatus.DRAFT) {
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_ONLY_DRAFT);
        }

        // 본문 비어있으면 거부
        String content = episode.getContent();
        if (content == null || content.isBlank()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_CONTENT_EMPTY);
        }

        // 포인트 잔액 체크 - 테스트할때 주석
        Long balance = pointService.getBalance(userId);
        if (balance < AI_REVIEW_COST) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT);
        }

        // OpenAI 호출 (매번 새로)
        log.info("[AI 리뷰 신규 생성] episodeId={}, userId={}", episodeId, userId);
        AiReviewResponse response;
        try {
            response = aiReviewClient.generate(episodeId, episode.getTitle(), content);
        } catch (RuntimeException e) {
            log.error("[AI 리뷰 호출/파싱 실패] episodeId={}", episodeId, e);
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_GENERATION_FAILED);
        }

        // AI 호출 후 포인트 차감 - 테스트할때 주석
        pointService.deductForAi(userId, AI_REVIEW_COST, episodeId);
        log.info("[AI 리뷰 포인트 차감] userId={}, amount={}P, episodeId={}",
                userId, AI_REVIEW_COST, episodeId);

        return response;
    }


    // AI 리뷰 비동기 요청 (v2)
    @Transactional
    public AiReviewJobResponse requestReviewAsync(Long episodeId, UserDetailsImpl userDetails) {

        Long userId = userDetails.getUser().getId();

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 분산락 (같은 회차 동시 요청 방지 -> 포인트 중복 차감 방지)
        RLock lock = redissonClient.getLock(AI_REVIEW_LOCK_KEY + episodeId);

        try {
            boolean acquired = lock.tryLock(0, 3, TimeUnit.SECONDS);
            if (!acquired) {
                throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_LOCK_ACQUIRE_FAILED);
            }

            // 회차 조회
            Episode episode = findEpisodeById(episodeId);

            // 본인 소설 확인
            validateOwnership(episode.getNovelId(), userId);

            // 발행 전(DRAFT) 회차만 AI 리뷰 가능
            if (episode.getStatus() != EpisodeStatus.DRAFT) {
                throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_ONLY_DRAFT);
            }

            // 본문 비어있으면 거부
            String content = episode.getContent();
            if (content == null || content.isBlank()) {
                throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_CONTENT_EMPTY);
            }

            // 포인트 잔액 사전 체크
            Long balance = pointService.getBalance(userId);
            if (balance < AI_REVIEW_COST) {
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT);
            }

            // Job 생성 (PROCESSING 상태)
            String jobId = UUID.randomUUID().toString();
            AiReviewJob job = AiReviewJob.create(jobId, episodeId, userId);
            saveJob(job);

            // Kafka 메시지 발행
            AiReviewMessage message = new AiReviewMessage(
                    jobId, episodeId, userId, episode.getTitle(), content
            );
            aiReviewProducer.send(message).exceptionally(ex -> {
                log.error("[AI 리뷰 Kafka 발행 실패] jobId={}", jobId, ex);
                AiReviewJob failedJob = findJob(jobId);
                if (failedJob != null) {
                    saveJob(failedJob.failed("Kafka 발행에 실패했습니다."));
                }
                return null;
            });

            log.info("[AI 리뷰 v2 요청] jobId={}, episodeId={}, userId={}",
                    jobId, episodeId, userId);

            return AiReviewJobResponse.processing(jobId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_LOCK_ACQUIRE_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // Job 상태 조회 (폴링용)
    public AiReviewJob getJobStatus(String jobId) {
        AiReviewJob job = findJob(jobId);
        if (job == null) {
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_JOB_NOT_FOUND);
        }
        return job;
    }


    // Consumer 백그라운드 처리
    @Transactional
    public void processReview(AiReviewMessage message) {
        String jobId = message.jobId();
        Long userId = message.userId();
        Long episodeId = message.episodeId();

        AiReviewJob currentJob = findJob(jobId);
        if (currentJob == null) {
            log.warn("[AI 리뷰 Consumer] Job 없음, 스킵 jobId={}", jobId);
            return;
        }
        if (currentJob.status() != AiReviewJobStatus.PROCESSING) {
            log.info("[AI 리뷰 Consumer] 이미 처리된 Job 스킵 jobId={}, status={}",
                    jobId, currentJob.status());
            return;
        }

        log.info("[AI 리뷰 Consumer 처리 시작] jobId={}, episodeId={}", jobId, episodeId);

        // OpenAI 호출
        AiReviewResponse response;
        try {
            response = aiReviewClient.generate(episodeId, message.title(), message.content());
        } catch (RuntimeException e) {
            log.error("[AI 리뷰 Consumer 처리 실패] jobId={}", jobId, e);
            AiReviewJob job = findJob(jobId);
            if (job != null) {
                saveJob(job.failed("AI 리뷰 생성에 실패했습니다."));
            }
            return;
        }

        // 포인트 차감 - 테스트할때 주석
        try {
            pointService.deductForAi(userId, AI_REVIEW_COST, episodeId);
            log.info("[AI 리뷰 v2 포인트 차감] userId={}, amount={}P, episodeId={}",
                    userId, AI_REVIEW_COST, episodeId);
        } catch (RuntimeException e) {
            log.error("[AI 리뷰 v2 포인트 차감 실패] jobId={}, userId={}", jobId, userId, e);
            AiReviewJob job = findJob(jobId);
            if (job != null) {
                saveJob(job.failed("포인트 차감에 실패했습니다."));
            }
            return;
        }

        // Job 완료 처리
        AiReviewJob job = findJob(jobId);
        if (job != null) {
            saveJob(job.completed(response));
            log.info("[AI 리뷰 Consumer 처리 완료] jobId={}", jobId);
        }
    }

    // ----------------------------- Redis Job 관리 -----------------------------

    private void saveJob(AiReviewJob job) {
        String key = JOB_KEY_PREFIX + job.jobId();
        try {
            String json = objectMapper.writeValueAsString(job);
            redisTemplate.opsForValue().set(key, json, JOB_TTL);
            log.debug("[Job SAVE] key={}, status={}", key, job.status());
        } catch (JsonProcessingException e) {
            log.error("[Job 직렬화 실패] jobId={}", job.jobId(), e);
            throw new IllegalStateException("Job 저장 실패", e);
        }
    }

    private AiReviewJob findJob(String jobId) {
        String key = JOB_KEY_PREFIX + jobId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) return null;

        try {
            return objectMapper.readValue(cached.toString(), AiReviewJob.class);
        } catch (JsonProcessingException e) {
            log.error("[Job 역직렬화 실패] jobId={}", jobId, e);
            return null;
        }
    }

    // ----------------------------- 공통 메서드 -----------------------------

    private void validateAuthorRole(UserDetailsImpl userDetails) {
        if (userDetails.getUser().getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_AUTHOR_FORBIDDEN);
        }
    }

    private Episode findEpisodeById(Long episodeId) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));

        if (episode.isDeleted()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_DELETED);
        }
        return episode;
    }

    private void validateOwnership(Long novelId, Long userId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        // 본인 소설 확인 (AI 리뷰 전용 메시지)
        if (!novel.getAuthorId().equals(userId)) {
            throw new ServiceErrorException(EpisodeExceptionEnum.AI_REVIEW_FORBIDDEN);
        }
    }
}