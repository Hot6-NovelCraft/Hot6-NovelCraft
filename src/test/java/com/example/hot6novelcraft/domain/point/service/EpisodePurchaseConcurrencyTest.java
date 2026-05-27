package com.example.hot6novelcraft.domain.point.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodePurchaseResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.NovelBulkPurchaseResponse;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("회차 구매 동시성 테스트")
class EpisodePurchaseConcurrencyTest {

    @InjectMocks
    private EpisodePurchaseFacade purchaseFacade;

    @Mock
    private EpisodePurchaseTransactionService transactionService;

    @Mock
    private RedisUtil redisUtil;

    private static final Long USER_ID = 1L;
    private static final Long EPISODE_ID = 100L;
    private static final Long NOVEL_ID = 10L;
    private static final int EPISODE_PRICE = 200;
    private static final Long INITIAL_BALANCE = 10000L;

    private Episode createMockEpisode(Long episodeId, Long novelId, int price) {
        Episode episode = mock(Episode.class);
        given(episode.getId()).willReturn(episodeId);
        given(episode.getNovelId()).willReturn(novelId);
        given(episode.getTitle()).willReturn("테스트 회차 " + episodeId);
        given(episode.getPointPrice()).willReturn(price);
        given(episode.getStatus()).willReturn(EpisodeStatus.PUBLISHED);
        given(episode.isFree()).willReturn(false);
        given(episode.isDeleted()).willReturn(false);
        return episode;
    }

    private EpisodePurchaseResponse createMockResponse(Long episodeId, Long remainingBalance) {
        return new EpisodePurchaseResponse(episodeId, null,"테스트 회차 " + episodeId, EPISODE_PRICE, remainingBalance);
    }

    // =========================================================
    // 단건 회차 구매 동시성 테스트
    // =========================================================
    @Nested
    @DisplayName("단건 회차 구매 동시성 테스트")
    class SingleEpisodePurchaseConcurrencyTest {

        @Test
        @DisplayName("락 획득 실패 시 ERR_PAYMENT_PROCESSING 예외 반환")
        void purchaseEpisode_whenLockNotAcquired_throwsPaymentProcessing() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage());

            verify(transactionService, never()).executePurchase(any(), any());
        }

        @Test
        @DisplayName("동일 회차 동시 구매 2개 - 락으로 1건만 성공")
        void purchaseEpisode_concurrentTwoRequests_onlyOneSucceeds() throws InterruptedException {
            // given
            AtomicBoolean lockHeld = new AtomicBoolean(false);
            given(redisUtil.acquireLock(anyString()))
                    .willAnswer(inv -> lockHeld.compareAndSet(false, true));

            EpisodePurchaseResponse mockResponse = createMockResponse(EPISODE_ID, INITIAL_BALANCE - EPISODE_PRICE);
            given(transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .willReturn(mockResponse);

            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            List<Exception> unexpectedErrors = new ArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID);
                        successCount.incrementAndGet();
                    } catch (ServiceErrorException e) {
                        if (e.getMessage().equals(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage())) {
                            failedCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(e);
                        }
                    } catch (Exception e) {
                        unexpectedErrors.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(completed).isTrue();
            assertThat(unexpectedErrors).isEmpty();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failedCount.get()).isEqualTo(1);
            verify(transactionService, times(1)).executePurchase(USER_ID, EPISODE_ID);
        }

        @Test
        @DisplayName("10개 동시 요청 중 락을 획득한 1개만 구매 실행")
        void purchaseEpisode_tenConcurrentRequests_onlyOneExecutes() throws InterruptedException {
            // given
            AtomicBoolean lockHeld = new AtomicBoolean(false);
            given(redisUtil.acquireLock(anyString()))
                    .willAnswer(inv -> lockHeld.compareAndSet(false, true));

            EpisodePurchaseResponse mockResponse = createMockResponse(EPISODE_ID, INITIAL_BALANCE - EPISODE_PRICE);
            given(transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .willReturn(mockResponse);

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger processingErrorCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID);
                    } catch (ServiceErrorException e) {
                        if (e.getMessage().equals(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage())) {
                            processingErrorCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(processingErrorCount.get()).isEqualTo(9);
            verify(transactionService, times(1)).executePurchase(USER_ID, EPISODE_ID);
        }

        @Test
        @DisplayName("트랜잭션 실패 시에도 finally에서 락 반드시 해제")
        void purchaseEpisode_whenTransactionFails_lockMustBeReleased() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT));

            // when & then
            assertThatThrownBy(() -> purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT.getMessage());

            verify(redisUtil, times(1)).releaseLock(eq("purchase:lock:" + USER_ID));
        }
    }

    // =========================================================
    // 소설 전체 구매 동시성 테스트
    // =========================================================
    @Nested
    @DisplayName("소설 전체 구매 동시성 테스트")
    class BulkPurchaseConcurrencyTest {

        @Test
        @DisplayName("락 획득 실패 시 ERR_PAYMENT_PROCESSING 예외 반환")
        void purchaseAllEpisodes_whenLockNotAcquired_throwsPaymentProcessing() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage());

            verify(transactionService, never()).executeAllPurchase(any(), any());
        }

        @Test
        @DisplayName("동일 소설 동시 전체 구매 2개 - 락으로 1건만 성공")
        void purchaseAllEpisodes_concurrentTwoRequests_onlyOneSucceeds() throws InterruptedException {
            // given
            AtomicBoolean lockHeld = new AtomicBoolean(false);
            given(redisUtil.acquireLock(anyString()))
                    .willAnswer(inv -> lockHeld.compareAndSet(false, true));

            NovelBulkPurchaseResponse mockResponse = new NovelBulkPurchaseResponse(
                    NOVEL_ID, 10, 2000, 10, 200, 1800, INITIAL_BALANCE - 1800, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
            );
            given(transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .willReturn(mockResponse);

            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            List<Exception> unexpectedErrors = new ArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID);
                        successCount.incrementAndGet();
                    } catch (ServiceErrorException e) {
                        if (e.getMessage().equals(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage())) {
                            failedCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(e);
                        }
                    } catch (Exception e) {
                        unexpectedErrors.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(completed).isTrue();
            assertThat(unexpectedErrors).isEmpty();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failedCount.get()).isEqualTo(1);
            verify(transactionService, times(1)).executeAllPurchase(USER_ID, NOVEL_ID);
        }

        @Test
        @DisplayName("10개 동시 요청 중 락을 획득한 1개만 전체 구매 실행")
        void purchaseAllEpisodes_tenConcurrentRequests_onlyOneExecutes() throws InterruptedException {
            // given
            AtomicBoolean lockHeld = new AtomicBoolean(false);
            given(redisUtil.acquireLock(anyString()))
                    .willAnswer(inv -> lockHeld.compareAndSet(false, true));

            NovelBulkPurchaseResponse mockResponse = new NovelBulkPurchaseResponse(
                    NOVEL_ID, 10, 2000, 10, 200, 1800, INITIAL_BALANCE - 1800, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
            );
            given(transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .willReturn(mockResponse);

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger processingErrorCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID);
                    } catch (ServiceErrorException e) {
                        if (e.getMessage().equals(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage())) {
                            processingErrorCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(processingErrorCount.get()).isEqualTo(9);
            verify(transactionService, times(1)).executeAllPurchase(USER_ID, NOVEL_ID);
        }
    }

    // =========================================================
    // 단건/전체 구매 혼합 동시성 테스트 (사용자 레벨 락 검증)
    // =========================================================
    @Nested
    @DisplayName("단건/전체 구매 혼합 동시성 테스트 (사용자 레벨 락)")
    class MixedPurchaseConcurrencyTest {

        @Test
        @DisplayName("동일 사용자 단건+전체 동시 요청 - 사용자 락으로 순차 실행")
        void mixedPurchase_sameUser_onlyOneExecutesAtATime() throws InterruptedException {
            // given
            AtomicBoolean lockHeld = new AtomicBoolean(false);
            given(redisUtil.acquireLock(eq("purchase:lock:" + USER_ID)))
                    .willAnswer(inv -> lockHeld.compareAndSet(false, true));

            EpisodePurchaseResponse singleResponse = createMockResponse(EPISODE_ID, INITIAL_BALANCE - EPISODE_PRICE);
            given(transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .willReturn(singleResponse);

            NovelBulkPurchaseResponse bulkResponse = new NovelBulkPurchaseResponse(
                    NOVEL_ID, 10, 2000, 10, 200, 1800, INITIAL_BALANCE - 1800, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
            );
            given(transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .willReturn(bulkResponse);

            int threadCount = 4; // 단건 2개 + 전체 2개
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger processingErrorCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when - 단건 2개, 전체 2개 동시 요청
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID);
                        successCount.incrementAndGet();
                    } catch (ServiceErrorException e) {
                        if (e.getMessage().equals(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage())) {
                            processingErrorCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID);
                        successCount.incrementAndGet();
                    } catch (ServiceErrorException e) {
                        if (e.getMessage().equals(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage())) {
                            processingErrorCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            // 사용자 레벨 락이므로 단건/전체 중 1개만 성공, 나머지 3개는 락 획득 실패
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(processingErrorCount.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("다른 사용자 동시 구매 - 각각 독립적으로 실행")
        void mixedPurchase_differentUsers_executeIndependently() throws InterruptedException {
            // given
            Long user1 = 1L;
            Long user2 = 2L;

            given(redisUtil.acquireLock(eq("purchase:lock:" + user1))).willReturn(true);
            given(redisUtil.acquireLock(eq("purchase:lock:" + user2))).willReturn(true);

            EpisodePurchaseResponse response1 = createMockResponse(EPISODE_ID, INITIAL_BALANCE - EPISODE_PRICE);
            EpisodePurchaseResponse response2 = createMockResponse(EPISODE_ID + 1, INITIAL_BALANCE - EPISODE_PRICE);

            given(transactionService.executePurchase(user1, EPISODE_ID)).willReturn(response1);
            given(transactionService.executePurchase(user2, EPISODE_ID + 1)).willReturn(response2);

            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when - 서로 다른 사용자가 동시에 구매
            executor.submit(() -> {
                try {
                    startLatch.await();
                    purchaseFacade.purchaseEpisode(user1, EPISODE_ID);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startLatch.await();
                    purchaseFacade.purchaseEpisode(user2, EPISODE_ID + 1);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then - 다른 사용자이므로 둘 다 성공
            assertThat(successCount.get()).isEqualTo(2);
            verify(transactionService, times(1)).executePurchase(user1, EPISODE_ID);
            verify(transactionService, times(1)).executePurchase(user2, EPISODE_ID + 1);
        }
    }
}
