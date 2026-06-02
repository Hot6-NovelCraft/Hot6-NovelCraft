package com.example.hot6novelcraft.domain.point.service;

import com.example.hot6novelcraft.common.config.EpisodePurchaseConfig;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EpisodeExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodePurchaseResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.NovelBulkPurchaseResponse;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.exchange.repository.RevenueRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.Point;
import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.point.repository.PointRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EpisodePurchaseTransactionService 테스트")
class EpisodePurchaseTransactionServiceTest {

    @InjectMocks
    private EpisodePurchaseTransactionService transactionService;

    @Mock
    private PointRepository pointRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private EpisodePurchaseConfig purchaseConfig;

    @Mock
    private NovelRepository novelRepository;

    @Mock
    private RevenueRepository revenueRepository;

    private static final Long USER_ID = 1L;
    private static final Long EPISODE_ID = 100L;
    private static final Long NOVEL_ID = 10L;
    private static final int EPISODE_PRICE = 200;
    private static final Long INITIAL_BALANCE = 10000L;

    private Episode createMockEpisode(Long episodeId, Long novelId, String title, int price, boolean isFree, EpisodeStatus status, boolean isDeleted) {
        Episode episode = mock(Episode.class);
        given(episode.getId()).willReturn(episodeId);
        given(episode.getNovelId()).willReturn(novelId);
        given(episode.getTitle()).willReturn(title);
        given(episode.getPointPrice()).willReturn(price);
        given(episode.isFree()).willReturn(isFree);
        given(episode.getStatus()).willReturn(status);
        given(episode.isDeleted()).willReturn(isDeleted);
        return episode;
    }

    private Point createMockPoint(Long userId, Long balance) {
        Point point = mock(Point.class);
        given(point.getUserId()).willReturn(userId);
        given(point.getBalance()).willReturn(balance);
        return point;
    }

    // =========================================================
    // 단건 회차 구매 트랜잭션 테스트
    // =========================================================
    @Nested
    @DisplayName("executePurchase() - 회차 단건 구매 트랜잭션")
    class ExecutePurchaseTest {

        @Test
        @DisplayName("성공 - 정상 구매 플로우")
        void executePurchase_success() {
            // given
            Episode episode = createMockEpisode(EPISODE_ID, NOVEL_ID, "테스트 회차", EPISODE_PRICE, false, EpisodeStatus.PUBLISHED, false);
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(episode));
            given(pointHistoryRepository.existsByUserIdAndEpisodeIdAndType(USER_ID, EPISODE_ID, PointHistoryType.NOVEL))
                    .willReturn(false);

            Point point = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID)).willReturn(Optional.of(point));

            // when
            EpisodePurchaseResponse result = transactionService.executePurchase(USER_ID, EPISODE_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.episodeId()).isEqualTo(EPISODE_ID);
            assertThat(result.pointPrice()).isEqualTo(EPISODE_PRICE);

            verify(point, times(1)).deduct((long) EPISODE_PRICE);
            verify(pointHistoryRepository, times(1)).save(any(PointHistory.class));
        }

        @Test
        @DisplayName("실패 - Episode를 찾을 수 없음")
        void executePurchase_episodeNotFound() {
            // given
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EpisodeExceptionEnum.EPISODE_NOT_FOUND.getMessage());

            verify(pointHistoryRepository, never()).existsByUserIdAndEpisodeIdAndType(any(), any(), any());
            verify(pointRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("실패 - 무료 회차 구매 시도")
        void executePurchase_freeEpisode() {
            // given
            Episode freeEpisode = createMockEpisode(EPISODE_ID, NOVEL_ID, "무료 회차", 0, true, EpisodeStatus.PUBLISHED, false);
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(freeEpisode));

            // when & then
            assertThatThrownBy(() -> transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EpisodeExceptionEnum.EPISODE_FREE_NO_PURCHASE.getMessage());

            verify(pointHistoryRepository, never()).existsByUserIdAndEpisodeIdAndType(any(), any(), any());
        }

        @Test
        @DisplayName("실패 - 미발행 회차 구매 시도")
        void executePurchase_draftEpisode() {
            // given
            Episode draftEpisode = createMockEpisode(EPISODE_ID, NOVEL_ID, "초안 회차", EPISODE_PRICE, false, EpisodeStatus.DRAFT, false);
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(draftEpisode));

            // when & then
            assertThatThrownBy(() -> transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EpisodeExceptionEnum.EPISODE_NOT_AVAILABLE_FOR_PURCHASE.getMessage());
        }

        @Test
        @DisplayName("실패 - 삭제된 회차 구매 시도")
        void executePurchase_deletedEpisode() {
            // given
            Episode deletedEpisode = createMockEpisode(EPISODE_ID, NOVEL_ID, "삭제된 회차", EPISODE_PRICE, false, EpisodeStatus.PUBLISHED, true);
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(deletedEpisode));

            // when & then
            assertThatThrownBy(() -> transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EpisodeExceptionEnum.EPISODE_NOT_AVAILABLE_FOR_PURCHASE.getMessage());
        }

        @Test
        @DisplayName("실패 - 이미 구매한 회차")
        void executePurchase_alreadyPurchased() {
            // given
            Episode episode = createMockEpisode(EPISODE_ID, NOVEL_ID, "테스트 회차", EPISODE_PRICE, false, EpisodeStatus.PUBLISHED, false);
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(episode));
            given(pointHistoryRepository.existsByUserIdAndEpisodeIdAndType(USER_ID, EPISODE_ID, PointHistoryType.NOVEL))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(() -> transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EpisodeExceptionEnum.EPISODE_ALREADY_PURCHASED.getMessage());

            verify(pointRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("실패 - Point가 존재하지 않음")
        void executePurchase_pointNotFound() {
            // given
            Episode episode = createMockEpisode(EPISODE_ID, NOVEL_ID, "테스트 회차", EPISODE_PRICE, false, EpisodeStatus.PUBLISHED, false);
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(episode));
            given(pointHistoryRepository.existsByUserIdAndEpisodeIdAndType(USER_ID, EPISODE_ID, PointHistoryType.NOVEL))
                    .willReturn(false);
            given(pointRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_POINT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 포인트 잔액 부족")
        void executePurchase_insufficientBalance() {
            // given
            Episode episode = createMockEpisode(EPISODE_ID, NOVEL_ID, "테스트 회차", EPISODE_PRICE, false, EpisodeStatus.PUBLISHED, false);
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(episode));
            given(pointHistoryRepository.existsByUserIdAndEpisodeIdAndType(USER_ID, EPISODE_ID, PointHistoryType.NOVEL))
                    .willReturn(false);

            Point point = createMockPoint(USER_ID, 100L);  // 잔액 부족
            given(pointRepository.findByUserId(USER_ID)).willReturn(Optional.of(point));

            // when & then
            assertThatThrownBy(() -> transactionService.executePurchase(USER_ID, EPISODE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT.getMessage());

            verify(point, never()).deduct(anyLong());
            verify(pointHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("검증 - PointHistory 정확하게 저장")
        void executePurchase_pointHistorySavedCorrectly() {
            // given
            Episode episode = createMockEpisode(EPISODE_ID, NOVEL_ID, "테스트 회차", EPISODE_PRICE, false, EpisodeStatus.PUBLISHED, false);
            given(episodeRepository.findById(EPISODE_ID)).willReturn(Optional.of(episode));
            given(pointHistoryRepository.existsByUserIdAndEpisodeIdAndType(USER_ID, EPISODE_ID, PointHistoryType.NOVEL))
                    .willReturn(false);

            Point point = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID)).willReturn(Optional.of(point));

            // when
            transactionService.executePurchase(USER_ID, EPISODE_ID);

            // then
            ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointHistoryRepository).save(historyCaptor.capture());
            PointHistory savedHistory = historyCaptor.getValue();

            assertThat(savedHistory.getUserId()).isEqualTo(USER_ID);
            assertThat(savedHistory.getNovelId()).isEqualTo(NOVEL_ID);
            assertThat(savedHistory.getEpisodeId()).isEqualTo(EPISODE_ID);
            assertThat(savedHistory.getAmount()).isEqualTo((long) EPISODE_PRICE);
            assertThat(savedHistory.getType()).isEqualTo(PointHistoryType.NOVEL);
            assertThat(savedHistory.getDescription()).contains("회차 구매");
        }
    }

    // =========================================================
    // 소설 전체 구매 트랜잭션 테스트
    // =========================================================
    @Nested
    @DisplayName("executeAllPurchase() - 소설 전체 구매 트랜잭션")
    class ExecuteAllPurchaseTest {

        @Test
        @DisplayName("성공 - 정상 전체 구매 플로우 (10% 할인)")
        void executeAllPurchase_success() {
            // given
            List<Episode> unpurchasedEpisodes = List.of(
                    createMockEpisode(1L, NOVEL_ID, "회차1", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(2L, NOVEL_ID, "회차2", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(3L, NOVEL_ID, "회차3", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(4L, NOVEL_ID, "회차4", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(5L, NOVEL_ID, "회차5", 200, false, EpisodeStatus.PUBLISHED, false)
            );

            given(episodeRepository.findPublishedPaidEpisodesByNovelId(NOVEL_ID))
                    .willReturn(unpurchasedEpisodes);
            given(pointHistoryRepository.findPurchasedEpisodeIds(USER_ID, NOVEL_ID, PointHistoryType.NOVEL))
                    .willReturn(List.of());  // 구매 이력 없음
            given(purchaseConfig.getDiscountRate()).willReturn(10);

            Point point = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID)).willReturn(Optional.of(point));

            // when
            NovelBulkPurchaseResponse result = transactionService.executeAllPurchase(USER_ID, NOVEL_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.novelId()).isEqualTo(NOVEL_ID);
            assertThat(result.totalEpisodes()).isEqualTo(5);
            assertThat(result.originalPrice()).isEqualTo(1000);
            assertThat(result.discountRate()).isEqualTo(10);
            assertThat(result.discountAmount()).isEqualTo(100);
            assertThat(result.finalPrice()).isEqualTo(900);

            verify(point, times(1)).deduct(900L);
            verify(pointHistoryRepository, times(1)).saveAll(anyList());
        }

        @Test
        @DisplayName("성공 - 할인 금액 회차별 분배 검증")
        void executeAllPurchase_discountDistributionCorrect() {
            // given
            List<Episode> unpurchasedEpisodes = List.of(
                    createMockEpisode(1L, NOVEL_ID, "회차1", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(2L, NOVEL_ID, "회차2", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(3L, NOVEL_ID, "회차3", 200, false, EpisodeStatus.PUBLISHED, false)
            );

            given(episodeRepository.findPublishedPaidEpisodesByNovelId(NOVEL_ID))
                    .willReturn(unpurchasedEpisodes);
            given(pointHistoryRepository.findPurchasedEpisodeIds(USER_ID, NOVEL_ID, PointHistoryType.NOVEL))
                    .willReturn(List.of());
            given(purchaseConfig.getDiscountRate()).willReturn(10);

            Point point = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID)).willReturn(Optional.of(point));

            // when
            transactionService.executeAllPurchase(USER_ID, NOVEL_ID);

            // then
            ArgumentCaptor<List<PointHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
            verify(pointHistoryRepository).saveAll(historyCaptor.capture());
            List<PointHistory> savedHistories = historyCaptor.getValue();

            assertThat(savedHistories).hasSize(3);

            // 할인 금액: 600 * 0.1 = 60P
            // 회차당: 60 / 3 = 20P
            // 나머지: 60 % 3 = 0P
            long totalSaved = savedHistories.stream().mapToLong(PointHistory::getAmount).sum();
            assertThat(totalSaved).isEqualTo(540L);  // 600 - 60 = 540
        }

        @Test
        @DisplayName("실패 - 구매 가능한 회차 없음")
        void executeAllPurchase_noPurchasableEpisodes() {
            // given
            given(episodeRepository.findPublishedPaidEpisodesByNovelId(NOVEL_ID))
                    .willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EpisodeExceptionEnum.NOVEL_NO_PURCHASABLE_EPISODES.getMessage());

            verify(pointRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("실패 - 모든 회차 이미 구매함")
        void executeAllPurchase_allEpisodesAlreadyPurchased() {
            // given
            List<Episode> allEpisodes = List.of(
                    createMockEpisode(1L, NOVEL_ID, "회차1", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(2L, NOVEL_ID, "회차2", 200, false, EpisodeStatus.PUBLISHED, false)
            );

            given(episodeRepository.findPublishedPaidEpisodesByNovelId(NOVEL_ID))
                    .willReturn(allEpisodes);
            given(pointHistoryRepository.findPurchasedEpisodeIds(USER_ID, NOVEL_ID, PointHistoryType.NOVEL))
                    .willReturn(List.of(1L, 2L));  // 모두 구매함

            // when & then
            assertThatThrownBy(() -> transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EpisodeExceptionEnum.NOVEL_NO_PURCHASABLE_EPISODES.getMessage());
        }

        @Test
        @DisplayName("실패 - Point가 존재하지 않음")
        void executeAllPurchase_pointNotFound() {
            // given
            List<Episode> unpurchasedEpisodes = List.of(
                    createMockEpisode(1L, NOVEL_ID, "회차1", 200, false, EpisodeStatus.PUBLISHED, false)
            );

            given(episodeRepository.findPublishedPaidEpisodesByNovelId(NOVEL_ID))
                    .willReturn(unpurchasedEpisodes);
            given(pointHistoryRepository.findPurchasedEpisodeIds(USER_ID, NOVEL_ID, PointHistoryType.NOVEL))
                    .willReturn(List.of());
            given(purchaseConfig.getDiscountRate()).willReturn(10);
            given(pointRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_POINT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 포인트 잔액 부족")
        void executeAllPurchase_insufficientBalance() {
            // given
            List<Episode> unpurchasedEpisodes = List.of(
                    createMockEpisode(1L, NOVEL_ID, "회차1", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(2L, NOVEL_ID, "회차2", 200, false, EpisodeStatus.PUBLISHED, false)
            );

            given(episodeRepository.findPublishedPaidEpisodesByNovelId(NOVEL_ID))
                    .willReturn(unpurchasedEpisodes);
            given(pointHistoryRepository.findPurchasedEpisodeIds(USER_ID, NOVEL_ID, PointHistoryType.NOVEL))
                    .willReturn(List.of());
            given(purchaseConfig.getDiscountRate()).willReturn(10);

            Point point = createMockPoint(USER_ID, 100L);  // 잔액 부족
            given(pointRepository.findByUserId(USER_ID)).willReturn(Optional.of(point));

            // when & then
            assertThatThrownBy(() -> transactionService.executeAllPurchase(USER_ID, NOVEL_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT.getMessage());

            verify(point, never()).deduct(anyLong());
            verify(pointHistoryRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("검증 - 일부 회차만 구매 시 미구매 회차만 처리")
        void executeAllPurchase_partiallyPurchased() {
            // given
            List<Episode> allEpisodes = List.of(
                    createMockEpisode(1L, NOVEL_ID, "회차1", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(2L, NOVEL_ID, "회차2", 200, false, EpisodeStatus.PUBLISHED, false),
                    createMockEpisode(3L, NOVEL_ID, "회차3", 200, false, EpisodeStatus.PUBLISHED, false)
            );

            given(episodeRepository.findPublishedPaidEpisodesByNovelId(NOVEL_ID))
                    .willReturn(allEpisodes);
            given(pointHistoryRepository.findPurchasedEpisodeIds(USER_ID, NOVEL_ID, PointHistoryType.NOVEL))
                    .willReturn(List.of(1L));  // 회차1만 구매함
            given(purchaseConfig.getDiscountRate()).willReturn(10);

            Point point = createMockPoint(USER_ID, INITIAL_BALANCE);
            given(pointRepository.findByUserId(USER_ID)).willReturn(Optional.of(point));

            // when
            NovelBulkPurchaseResponse result = transactionService.executeAllPurchase(USER_ID, NOVEL_ID);

            // then
            assertThat(result.totalEpisodes()).isEqualTo(2);  // 미구매 회차 2개만
            assertThat(result.originalPrice()).isEqualTo(400);  // 200 * 2
            assertThat(result.finalPrice()).isEqualTo(360);     // 400 - 40
        }
    }
}
