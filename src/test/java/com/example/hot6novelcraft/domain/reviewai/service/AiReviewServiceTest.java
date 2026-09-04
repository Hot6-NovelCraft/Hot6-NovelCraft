package com.example.hot6novelcraft.domain.reviewai.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
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
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class AiReviewServiceTest {

    @Mock EpisodeRepository episodeRepository;
    @Mock NovelRepository novelRepository;
    @Mock AiReviewClient aiReviewClient;
    @Mock AiReviewProducer aiReviewProducer;
    @Mock PointService pointService;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOperations;
    @Mock ObjectMapper objectMapper;
    @Mock RedissonClient redissonClient;  // ← 추가
    @Mock RLock rLock;                    // ← 추가

    @InjectMocks
    AiReviewService aiReviewService;

    // ===================== Mock 헬퍼 메서드 =====================

    private UserDetailsImpl 작가() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getRole()).willReturn(UserRole.AUTHOR);
        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(user);
        return userDetails;
    }

    private UserDetailsImpl 독자() {
        User user = mock(User.class);
        given(user.getId()).willReturn(2L);
        given(user.getRole()).willReturn(UserRole.READER);
        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(user);
        return userDetails;
    }

    private Novel 소설(Long authorId) {
        Novel novel = mock(Novel.class);
        given(novel.getId()).willReturn(1L);
        given(novel.getAuthorId()).willReturn(authorId);
        return novel;
    }

    private Episode 회차(EpisodeStatus status, String content) {
        Episode episode = mock(Episode.class);
        given(episode.getId()).willReturn(1L);
        given(episode.getNovelId()).willReturn(1L);
        given(episode.getTitle()).willReturn("테스트 회차");
        given(episode.getContent()).willReturn(content);
        given(episode.getStatus()).willReturn(status);
        given(episode.isDeleted()).willReturn(false);
        return episode;
    }

    private AiReviewResponse AI리뷰응답() {
        return new AiReviewResponse(
                1L,
                List.of(
                        new AiReviewResponse.AiCommentResponse("달빛독자", "와 미쳤다 😭", 4.5),
                        new AiReviewResponse.AiCommentResponse("소설덕후", "다음화 빨리요!", 4.0)
                )
        );
    }

    private AiReviewJob PROCESSING상태_Job() {
        return AiReviewJob.create("test-job-id", 1L, 1L);
    }

    private AiReviewJob COMPLETED상태_Job() {
        return PROCESSING상태_Job().completed(AI리뷰응답());
    }

    private AiReviewJob FAILED상태_Job() {
        return PROCESSING상태_Job().failed("AI 리뷰 생성에 실패했습니다.");
    }

    private void Redis_Job저장_설정() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    private void Redis_Job조회_설정(String jobJson) {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(jobJson);
    }

    // 락 획득 성공 설정
    private void 락_획득_성공() throws InterruptedException {
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
    }

    // 락 획득 실패 설정
    private void 락_획득_실패() throws InterruptedException {
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(false);
    }

    // ===================== v1 - getReview() =====================

    @Test
    void v1_AI리뷰_성공() throws Exception {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.DRAFT, "소설 본문 내용입니다.");
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(pointService.getBalance(1L)).willReturn(500L);
        given(aiReviewClient.generate(anyLong(), anyString(), anyString())).willReturn(AI리뷰응답());

        AiReviewResponse result = aiReviewService.getReview(1L, userDetails);

        assertNotNull(result);
        assertEquals(2, result.comments().size());
        verify(pointService).deductForAi(1L, 200L, 1L);
    }

    @Test
    void v1_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.getReview(1L, userDetails));
    }

    @Test
    void v1_회차없으면_실패() {
        UserDetailsImpl userDetails = 작가();

        given(episodeRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.getReview(1L, userDetails));
    }

    @Test
    void v1_삭제된회차면_실패() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.DRAFT, "내용");
        given(episode.isDeleted()).willReturn(true);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.getReview(1L, userDetails));
    }

    @Test
    void v1_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.DRAFT, "내용");
        Novel novel = 소설(2L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.getReview(1L, userDetails));
    }

    @Test
    void v1_DRAFT아니면_실패() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.PUBLISHED, "내용");
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.getReview(1L, userDetails));
    }

    @Test
    void v1_본문비어있으면_실패() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.DRAFT, "");
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.getReview(1L, userDetails));
    }

    @Test
    void v1_포인트부족하면_실패() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.DRAFT, "소설 본문 내용입니다.");
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(pointService.getBalance(1L)).willReturn(100L);

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.getReview(1L, userDetails));
    }

    @Test
    void v1_OpenAI실패시_예외발생하고_포인트차감안됨() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.DRAFT, "소설 본문 내용입니다.");
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(pointService.getBalance(1L)).willReturn(500L);
        given(aiReviewClient.generate(anyLong(), anyString(), anyString()))
                .willThrow(new RuntimeException("OpenAI 호출 실패"));

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.getReview(1L, userDetails));

        verify(pointService, never()).deductForAi(anyLong(), anyLong(), anyLong());
    }

    // ===================== v2 - requestReviewAsync() =====================

    @Test
    void v2_비동기요청_성공_jobId반환() throws Exception {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.DRAFT, "소설 본문 내용입니다.");
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(pointService.getBalance(1L)).willReturn(500L);
        락_획득_성공();  // ← 추가
        Redis_Job저장_설정();
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(aiReviewProducer.send(any())).willReturn(CompletableFuture.completedFuture(null));

        AiReviewJobResponse result = aiReviewService.requestReviewAsync(1L, userDetails);

        assertNotNull(result);
        assertNotNull(result.jobId());
        assertEquals(AiReviewJobStatus.PROCESSING, result.status());
    }

    @Test
    void v2_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.requestReviewAsync(1L, userDetails));
    }

    @Test
    void v2_본인소설아니면_실패() throws Exception {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.DRAFT, "내용");
        Novel novel = 소설(2L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        락_획득_성공();  // ← 추가

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.requestReviewAsync(1L, userDetails));
    }

    @Test
    void v2_DRAFT아니면_실패() throws Exception {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.PUBLISHED, "내용");
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        락_획득_성공();  // ← 추가

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.requestReviewAsync(1L, userDetails));
    }

    @Test
    void v2_포인트부족하면_Kafka발행안함() throws Exception {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(EpisodeStatus.DRAFT, "소설 본문 내용입니다.");
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(pointService.getBalance(1L)).willReturn(100L);
        락_획득_성공();  // ← 추가

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.requestReviewAsync(1L, userDetails));

        verify(aiReviewProducer, never()).send(any());
    }

    @Test
    void v2_분산락획득실패시_예외발생() throws Exception {
        UserDetailsImpl userDetails = 작가();
        락_획득_실패();  // ← 락 실패!

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.requestReviewAsync(1L, userDetails));

        verify(aiReviewProducer, never()).send(any());
    }

    // ===================== 동시성 테스트 =====================

    @Test
    void v2_동시요청_분산락으로_1건만처리() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);   // 동시 출발
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);   // 성공 횟수
        AtomicInteger failCount = new AtomicInteger(0);       // 실패 횟수 (락 거절)

        // 첫 번째 요청만 락 획득 성공, 나머지는 실패
        AtomicInteger lockCallCount = new AtomicInteger(0);
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willAnswer(inv -> {
            int callCount = lockCallCount.incrementAndGet();
            return callCount == 1; // 첫 번째만 true, 나머지는 false
        });
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        // 정상 처리 Mock 설정
        Episode episode = 회차(EpisodeStatus.DRAFT, "소설 본문 내용입니다.");
        Novel novel = 소설(1L);
        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(pointService.getBalance(1L)).willReturn(500L);
        Redis_Job저장_설정();
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(aiReviewProducer.send(any())).willReturn(CompletableFuture.completedFuture(null));

        UserDetailsImpl userDetails = 작가();

        // 5개 스레드 동시 실행
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 동시 출발 대기
                    aiReviewService.requestReviewAsync(1L, userDetails);
                    successCount.incrementAndGet();
                } catch (ServiceErrorException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();                    // 동시 출발!
        doneLatch.await(10, TimeUnit.SECONDS);     // 완료 대기

        executor.shutdown();

        System.out.println("성공: " + successCount.get() + "건");
        System.out.println("실패(락 거절): " + failCount.get() + "건");

        // 5개 동시 요청 중 1개만 성공, 4개는 락 거절
        assertEquals(1, successCount.get(), "락으로 인해 1건만 성공해야 함");
        assertEquals(4, failCount.get(), "나머지 4건은 락 거절");
        // Kafka 발행은 1번만
        verify(aiReviewProducer, times(1)).send(any());
    }

    // ===================== processReview() =====================

    @Test
    void processReview_성공_Job_COMPLETED처리() throws Exception {
        AiReviewMessage message = new AiReviewMessage(
                "test-job-id", 1L, 1L, "테스트 제목", "소설 본문 내용입니다."
        );

        Redis_Job조회_설정("{}");
        given(objectMapper.readValue(anyString(), eq(AiReviewJob.class)))
                .willReturn(PROCESSING상태_Job());
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(aiReviewClient.generate(anyLong(), anyString(), anyString()))
                .willReturn(AI리뷰응답());

        aiReviewService.processReview(message);

        verify(pointService).deductForAi(1L, 200L, 1L);
        verify(valueOperations, atLeastOnce()).set(anyString(), anyString(), any());
    }

    @Test
    void processReview_Job없으면_스킵() {
        AiReviewMessage message = new AiReviewMessage(
                "test-job-id", 1L, 1L, "테스트 제목", "소설 본문"
        );

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        aiReviewService.processReview(message);

        verify(aiReviewClient, never()).generate(anyLong(), anyString(), anyString());
        verify(pointService, never()).deductForAi(anyLong(), anyLong(), anyLong());
    }

    @Test
    void processReview_이미완료된_Job이면_스킵() throws Exception {
        AiReviewMessage message = new AiReviewMessage(
                "test-job-id", 1L, 1L, "테스트 제목", "소설 본문"
        );

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("{}");
        given(objectMapper.readValue(anyString(), eq(AiReviewJob.class)))
                .willReturn(COMPLETED상태_Job());

        aiReviewService.processReview(message);

        verify(aiReviewClient, never()).generate(anyLong(), anyString(), anyString());
        verify(pointService, never()).deductForAi(anyLong(), anyLong(), anyLong());
    }

    @Test
    void processReview_이미실패한_Job이면_스킵() throws Exception {
        AiReviewMessage message = new AiReviewMessage(
                "test-job-id", 1L, 1L, "테스트 제목", "소설 본문"
        );

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("{}");
        given(objectMapper.readValue(anyString(), eq(AiReviewJob.class)))
                .willReturn(FAILED상태_Job());

        aiReviewService.processReview(message);

        verify(aiReviewClient, never()).generate(anyLong(), anyString(), anyString());
    }

    @Test
    void processReview_OpenAI실패시_Job_FAILED처리_포인트차감안됨() throws Exception {
        AiReviewMessage message = new AiReviewMessage(
                "test-job-id", 1L, 1L, "테스트 제목", "소설 본문 내용입니다."
        );

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("{}");
        given(objectMapper.readValue(anyString(), eq(AiReviewJob.class)))
                .willReturn(PROCESSING상태_Job());
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(aiReviewClient.generate(anyLong(), anyString(), anyString()))
                .willThrow(new RuntimeException("OpenAI 호출 실패"));

        aiReviewService.processReview(message);

        verify(pointService, never()).deductForAi(anyLong(), anyLong(), anyLong());
    }

    @Test
    void processReview_포인트차감실패시_Job_FAILED처리() throws Exception {
        AiReviewMessage message = new AiReviewMessage(
                "test-job-id", 1L, 1L, "테스트 제목", "소설 본문 내용입니다."
        );

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("{}");
        given(objectMapper.readValue(anyString(), eq(AiReviewJob.class)))
                .willReturn(PROCESSING상태_Job());
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(aiReviewClient.generate(anyLong(), anyString(), anyString()))
                .willReturn(AI리뷰응답());
        doThrow(new RuntimeException("포인트 부족"))
                .when(pointService).deductForAi(anyLong(), anyLong(), anyLong());

        aiReviewService.processReview(message);

        verify(valueOperations, atLeastOnce()).set(anyString(), anyString(), any());
    }

    // ===================== getJobStatus() =====================

    @Test
    void getJobStatus_성공() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("{}");
        given(objectMapper.readValue(anyString(), eq(AiReviewJob.class)))
                .willReturn(COMPLETED상태_Job());

        AiReviewJob result = aiReviewService.getJobStatus("test-job-id");

        assertNotNull(result);
        assertEquals(AiReviewJobStatus.COMPLETED, result.status());
    }

    @Test
    void getJobStatus_없는jobId면_실패() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        assertThrows(ServiceErrorException.class,
                () -> aiReviewService.getJobStatus("없는-job-id"));
    }

    @Test
    void getJobStatus_PROCESSING상태_조회() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("{}");
        given(objectMapper.readValue(anyString(), eq(AiReviewJob.class)))
                .willReturn(PROCESSING상태_Job());

        AiReviewJob result = aiReviewService.getJobStatus("test-job-id");

        assertEquals(AiReviewJobStatus.PROCESSING, result.status());
        assertNull(result.result());
    }

    @Test
    void getJobStatus_FAILED상태_조회() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("{}");
        given(objectMapper.readValue(anyString(), eq(AiReviewJob.class)))
                .willReturn(FAILED상태_Job());

        AiReviewJob result = aiReviewService.getJobStatus("test-job-id");

        assertEquals(AiReviewJobStatus.FAILED, result.status());
        assertNotNull(result.errorMessage());
    }
}