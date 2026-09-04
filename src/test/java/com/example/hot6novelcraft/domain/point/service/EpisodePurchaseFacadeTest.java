package com.example.hot6novelcraft.domain.point.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EpisodeExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodePurchaseResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.NovelBulkPurchaseResponse;
import com.example.hot6novelcraft.domain.exchange.service.RevenueService;
import com.example.hot6novelcraft.domain.exchange.service.StatisticsService;
import com.example.hot6novelcraft.domain.notification.producer.NotificationProducer;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EpisodePurchaseFacade 테스트")
class EpisodePurchaseFacadeTest {

    @InjectMocks
    private EpisodePurchaseFacade purchaseFacade;

    @Mock
    private EpisodePurchaseTransactionService transactionService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private NotificationProducer notificationProducer;

    @Mock
    private NovelRepository novelRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private RevenueService revenueService;

    @Mock
    private StatisticsService statisticsService;

    private static final Long USER_ID = 1L;
    private static final Long EPISODE_ID = 100L;
    private static final Long NOVEL_ID = 10L;
    private static final int EPISODE_PRICE = 200;
    private static final Long REMAINING_BALANCE = 9800L;

    // =========================================================
    // 단건 회차 구매 Facade 테스트
    // =========================================================
    @Nested
    @DisplayName("purchaseEpisode() - 회차 단건 구매 Facade")
    class PurchaseEpisodeTest {

        @Test
        @DisplayName("성공 - 락 획득, 트랜잭션 실행, 락 해제")
        void purchaseEpisode_success() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            EpisodePurchaseResponse mockResponse = new EpisodePurchaseResponse(
                    EPISODE_ID, NOVEL_ID,"테스트 회차", EPISODE_PRICE, REMAINING_BALANCE
            );
            given(transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .willReturn(mockResponse);

            // when
            EpisodePurchaseResponse result = purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.episodeId()).isEqualTo(EPISODE_ID);
            assertThat(result.remainingBalance()).isEqualTo(REMAINING_BALANCE);

            verify(redisUtil, times(1)).acquireLock(eq("purchase:lock:" + USER_ID));
            verify(transactionService, times(1)).executePurchase(USER_ID, EPISODE_ID);
            verify(redisUtil, times(1)).releaseLock(eq("purchase:lock:" + USER_ID));
        }

        @Test
        @DisplayName("실패 - 락 획득 실패 시 ERR_PAYMENT_PROCESSING 예외")
        void purchaseEpisode_lockNotAcquired_throwsException() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage());

            verify(transactionService, never()).executePurchase(any(), any());
            verify(redisUtil, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("실패 - 트랜잭션 실패 시에도 finally에서 락 해제")
        void purchaseEpisode_transactionFails_lockReleasedInFinally() {
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

        @Test
        @DisplayName("실패 - 이미 구매한 회차 예외 발생 시에도 락 해제")
        void purchaseEpisode_alreadyPurchased_lockReleased() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .willThrow(new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_PURCHASED));

            // when & then
            assertThatThrownBy(() -> purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EpisodeExceptionEnum.EPISODE_ALREADY_PURCHASED.getMessage());

            verify(redisUtil, times(1)).releaseLock(eq("purchase:lock:" + USER_ID));
        }

        @Test
        @DisplayName("검증 - 사용자 레벨 락 키 사용")
        void purchaseEpisode_usesUserLevelLockKey() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);
            EpisodePurchaseResponse mockResponse = new EpisodePurchaseResponse(
                    EPISODE_ID, NOVEL_ID,"테스트 회차", EPISODE_PRICE, REMAINING_BALANCE
            );
            given(transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .willReturn(mockResponse);

            // when
            purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID);

            // then
            String expectedLockKey = "purchase:lock:" + USER_ID;
            verify(redisUtil, times(1)).acquireLock(eq(expectedLockKey));
            verify(redisUtil, times(1)).releaseLock(eq(expectedLockKey));
        }
    }

    // =========================================================
    // 소설 전체 구매 Facade 테스트
    // =========================================================
    @Nested
    @DisplayName("purchaseAllEpisodes() - 소설 전체 구매 Facade")
    class PurchaseAllEpisodesTest {

        @Test
        @DisplayName("성공 - 락 획득, 트랜잭션 실행, 락 해제")
        void purchaseAllEpisodes_success() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            NovelBulkPurchaseResponse mockResponse = new NovelBulkPurchaseResponse(
                    NOVEL_ID, 10, 2000, 10, 200, 1800,
                    REMAINING_BALANCE - 1800,
                    List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
            );
            given(transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .willReturn(mockResponse);

            // when
            NovelBulkPurchaseResponse result = purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.novelId()).isEqualTo(NOVEL_ID);
            assertThat(result.totalEpisodes()).isEqualTo(10);
            assertThat(result.discountRate()).isEqualTo(10);

            verify(redisUtil, times(1)).acquireLock(eq("purchase:lock:" + USER_ID));
            verify(transactionService, times(1)).executeAllPurchase(USER_ID, NOVEL_ID);
            verify(redisUtil, times(1)).releaseLock(eq("purchase:lock:" + USER_ID));
        }

        @Test
        @DisplayName("실패 - 락 획득 실패 시 ERR_PAYMENT_PROCESSING 예외")
        void purchaseAllEpisodes_lockNotAcquired_throwsException() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage());

            verify(transactionService, never()).executeAllPurchase(any(), any());
            verify(redisUtil, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("실패 - 트랜잭션 실패 시에도 finally에서 락 해제")
        void purchaseAllEpisodes_transactionFails_lockReleasedInFinally() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT));

            // when & then
            assertThatThrownBy(() -> purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT.getMessage());

            verify(redisUtil, times(1)).releaseLock(eq("purchase:lock:" + USER_ID));
        }

        @Test
        @DisplayName("실패 - 구매 가능한 회차 없음 예외 발생 시에도 락 해제")
        void purchaseAllEpisodes_noPurchasableEpisodes_lockReleased() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .willThrow(new ServiceErrorException(EpisodeExceptionEnum.NOVEL_NO_PURCHASABLE_EPISODES));

            // when & then
            assertThatThrownBy(() -> purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EpisodeExceptionEnum.NOVEL_NO_PURCHASABLE_EPISODES.getMessage());

            verify(redisUtil, times(1)).releaseLock(eq("purchase:lock:" + USER_ID));
        }

        @Test
        @DisplayName("검증 - 사용자 레벨 락 키 사용 (단건 구매와 동일)")
        void purchaseAllEpisodes_usesUserLevelLockKey() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);
            NovelBulkPurchaseResponse mockResponse = new NovelBulkPurchaseResponse(
                    NOVEL_ID, 10, 2000, 10, 200, 1800,
                    REMAINING_BALANCE - 1800,
                    List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
            );
            given(transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .willReturn(mockResponse);

            // when
            purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID);

            // then
            String expectedLockKey = "purchase:lock:" + USER_ID;
            verify(redisUtil, times(1)).acquireLock(eq(expectedLockKey));
            verify(redisUtil, times(1)).releaseLock(eq(expectedLockKey));
        }
    }

    // =========================================================
    // 단건/전체 구매 락 키 일관성 테스트
    // =========================================================
    @Nested
    @DisplayName("단건/전체 구매 락 키 일관성")
    class LockKeyConsistencyTest {

        @Test
        @DisplayName("단건 구매와 전체 구매가 동일한 락 키 사용 (사용자 레벨)")
        void singleAndBulkPurchase_useSameLockKey() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            EpisodePurchaseResponse singleResponse = new EpisodePurchaseResponse(
                    EPISODE_ID, NOVEL_ID,"테스트 회차", EPISODE_PRICE, REMAINING_BALANCE
            );
            given(transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .willReturn(singleResponse);

            NovelBulkPurchaseResponse bulkResponse = new NovelBulkPurchaseResponse(
                    NOVEL_ID, 10, 2000, 10, 200, 1800,
                    REMAINING_BALANCE - 1800,
                    List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
            );
            given(transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .willReturn(bulkResponse);

            // when
            purchaseFacade.purchaseEpisode(USER_ID, EPISODE_ID);
            purchaseFacade.purchaseAllEpisodes(USER_ID, NOVEL_ID);

            // then
            String expectedLockKey = "purchase:lock:" + USER_ID;
            verify(redisUtil, times(2)).acquireLock(eq(expectedLockKey));
            verify(redisUtil, times(2)).releaseLock(eq(expectedLockKey));
        }
    }
}
