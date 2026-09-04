package com.example.hot6novelcraft.domain.point.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodePurchaseResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.NovelBulkPurchaseResponse;
import com.example.hot6novelcraft.domain.exchange.service.RevenueService;
import com.example.hot6novelcraft.domain.exchange.service.StatisticsService;
import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import com.example.hot6novelcraft.domain.notification.producer.NotificationProducer;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 회차 구매 Facade
 * Redis Lock 관리 담당 (트랜잭션 외부)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EpisodePurchaseFacade {

    private final EpisodePurchaseTransactionService transactionService;
    private final RedisUtil redisUtil;
    private final NotificationProducer notificationProducer;
    private final NovelRepository novelRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final RevenueService revenueService;
    private final StatisticsService statisticsService;

    /**
     * 회차 단건 구매 (락 관리)
     */
    public EpisodePurchaseResponse purchaseEpisode(Long userId, Long episodeId) {
        log.info("[회차 구매] 요청 userId={} episodeId={}", userId, episodeId);

        // 사용자 단위 락 (단건/전체 구매 레이스 컨디션 방지)
        String lockKey = "purchase:lock:" + userId;
        if (!redisUtil.acquireLock(lockKey)) {
            log.warn("[회차 구매] Lock 획득 실패 (이미 처리 중) userId={}", userId);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING);
        }

        try {
            EpisodePurchaseResponse response = transactionService.executePurchase(userId, episodeId);
            notificationProducer.publish(NotificationEvent.episodePurchase(userId, response.episodeTitle(), response.pointPrice(), episodeId));
            // 트랜잭션 커밋 후 작가 수익 캐시 무효화
            novelRepository.findById(response.novelId()).ifPresent(novel -> {
                revenueService.evictRevenueOverviewCache(novel.getAuthorId());
                statisticsService.evictStatisticsCache(novel.getAuthorId());
            });
            return response;
        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }

    /**
     * 소설 전체 구매 (락 관리)
     */
    public NovelBulkPurchaseResponse purchaseAllEpisodes(Long userId, Long novelId) {
        log.info("[소설 전체 구매] 요청 userId={} novelId={}", userId, novelId);

        // 사용자 단위 락 (단건/전체 구매 레이스 컨디션 방지)
        String lockKey = "purchase:lock:" + userId;
        if (!redisUtil.acquireLock(lockKey)) {
            log.warn("[소설 전체 구매] Lock 획득 실패 (이미 처리 중) userId={}", userId);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING);
        }

        try {
            NovelBulkPurchaseResponse response = transactionService.executeAllPurchase(userId, novelId);
            novelRepository.findById(novelId).ifPresent(novel -> {
                String novelTitle = novel.getTitle();
                notificationProducer.publish(NotificationEvent.novelBulkPurchase(userId, novelTitle, response.totalEpisodes(), response.finalPrice(), novelId));
                // 트랜잭션 커밋 후 작가 수익 캐시 무효화
                revenueService.evictRevenueOverviewCache(novel.getAuthorId());
                statisticsService.evictStatisticsCache(novel.getAuthorId());
            });
            return response;
        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }

    /**
     * 해당 소설에서 유저가 구매한 회차 ID 목록 조회
     */
    public List<Long> getPurchasedEpisodeIds(Long userId, Long novelId) {
        return pointHistoryRepository.findPurchasedEpisodeIds(userId, novelId, PointHistoryType.NOVEL);
    }
}
