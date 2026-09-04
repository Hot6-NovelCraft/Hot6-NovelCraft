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
import com.example.hot6novelcraft.domain.exchange.entity.Revenue;
import com.example.hot6novelcraft.domain.exchange.entity.enums.RevenueType;
import com.example.hot6novelcraft.domain.exchange.repository.RevenueRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.Point;
import com.example.hot6novelcraft.domain.point.entity.PointHistory;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 회차 구매 트랜잭션 처리
 * DB 작업만 담당 (@Transactional 적용)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EpisodePurchaseTransactionService {

    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final EpisodeRepository episodeRepository;
    private final EpisodePurchaseConfig purchaseConfig;
    private final NovelRepository novelRepository;
    private final RevenueRepository revenueRepository;

    /**
     * 회차 단건 구매 (트랜잭션 처리)
     */
    @Transactional
    public EpisodePurchaseResponse executePurchase(Long userId, Long episodeId) {
        // 1. Episode 조회 및 검증
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));
        validatePurchasable(episode);

        // 2. 중복 구매 체크
        boolean alreadyPurchased = pointHistoryRepository.existsByUserIdAndEpisodeIdAndType(
                userId, episodeId, PointHistoryType.NOVEL
        );
        if (alreadyPurchased) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_PURCHASED);
        }

        // 3. 포인트 잔액 조회 및 검증
        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_POINT_NOT_FOUND));

        if (point.getBalance() < episode.getPointPrice()) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT);
        }

        // 4. 포인트 차감
        point.deduct((long) episode.getPointPrice());
        log.info("[회차 구매] 포인트 차감 완료 userId={} amount={}P", userId, episode.getPointPrice());

        // 5. PointHistory 기록
        pointHistoryRepository.save(
                PointHistory.create(
                        userId,
                        episode.getNovelId(),
                        episodeId,
                        (long) episode.getPointPrice(),
                        PointHistoryType.NOVEL,
                        "회차 구매: " + episode.getTitle()
                )
        );

        // 6. 작가 수익 기록 (EPISODE_SALE)
        novelRepository.findById(episode.getNovelId()).ifPresent(novel -> {
            Long authorId = novel.getAuthorId();
            Integer earned = revenueRepository.sumAmountByAuthorIdAndTypeIn(
                    authorId, List.of(RevenueType.EPISODE_SALE, RevenueType.SUBSCRIPTION, RevenueType.REFUND));
            Integer withdrawn = revenueRepository.sumAmountByAuthorIdAndType(authorId, RevenueType.WITHDRAWAL);
            int newBalance = earned - withdrawn + episode.getPointPrice();
            revenueRepository.save(
                    Revenue.create(authorId, episodeId, episode.getPointPrice(), newBalance, RevenueType.EPISODE_SALE)
            );
            log.info("[회차 구매] 작가 수익 기록 authorId={} episodeId={} amount={}P", authorId, episodeId, episode.getPointPrice());
        });

        log.info("[회차 구매] 구매 완료 userId={} episodeId={} price={}P balance={}P",
                userId, episodeId, episode.getPointPrice(), point.getBalance());

        // 6. 응답 생성
        return EpisodePurchaseResponse.of(episode, point.getBalance());
    }

    /**
     * 소설 전체 구매 (트랜잭션 처리)
     */
    @Transactional
    public NovelBulkPurchaseResponse executeAllPurchase(Long userId, Long novelId) {
        // 1. 미구매 회차 목록 조회
        List<Episode> unpurchasedEpisodes = getUnpurchasedEpisodes(userId, novelId);

        if (unpurchasedEpisodes.isEmpty()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.NOVEL_NO_PURCHASABLE_EPISODES);
        }

        // 2. 가격 계산 (할인 적용)
        int originalPrice = unpurchasedEpisodes.stream()
                .mapToInt(Episode::getPointPrice)
                .sum();

        int discountRate = purchaseConfig.getDiscountRate();
        int discountAmount = originalPrice * discountRate / 100;
        int finalPrice = originalPrice - discountAmount;

        log.info("[소설 전체 구매] 가격 계산 userId={} 원가={}P 할인={}% 최종={}P",
                userId, originalPrice, discountRate, finalPrice);

        // 3. 포인트 잔액 조회 및 검증
        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_POINT_NOT_FOUND));

        if (point.getBalance() < finalPrice) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT);
        }

        // 4. 포인트 차감 (한 번에)
        point.deduct((long) finalPrice);
        log.info("[소설 전체 구매] 포인트 차감 완료 userId={} amount={}P", userId, finalPrice);

        // 5. PointHistory 일괄 저장 (할인 금액을 회차별로 분배)
        int episodeCount = unpurchasedEpisodes.size();
        int discountPerEpisode = discountAmount / episodeCount;  // 회차당 할인 금액
        int discountRemainder = discountAmount % episodeCount;   // 나머지 할인 금액

        List<PointHistory> histories = new ArrayList<>();
        for (int i = 0; i < episodeCount; i++) {
            Episode ep = unpurchasedEpisodes.get(i);

            // 할인 적용된 실제 금액 (나머지는 첫 번째 회차에 추가 분배)
            int actualPrice = ep.getPointPrice() - discountPerEpisode - (i == 0 ? discountRemainder : 0);

            histories.add(PointHistory.create(
                    userId,
                    novelId,
                    ep.getId(),
                    (long) actualPrice,  // 할인 적용된 금액
                    PointHistoryType.NOVEL,
                    String.format("소설 전체 구매 (할인: %d%%)", discountRate)
            ));
        }

        pointHistoryRepository.saveAll(histories);

        log.info("[소설 전체 구매] PointHistory 저장 완료 - 회차당 평균 할인: {}P", discountPerEpisode);

        // 6. 작가 수익 일괄 기록 (EPISODE_SALE, 할인 적용 금액 기준)
        novelRepository.findById(novelId).ifPresent(novel -> {
            Long authorId = novel.getAuthorId();
            Integer earned = revenueRepository.sumAmountByAuthorIdAndTypeIn(
                    authorId, List.of(RevenueType.EPISODE_SALE, RevenueType.SUBSCRIPTION, RevenueType.REFUND));
            Integer withdrawn = revenueRepository.sumAmountByAuthorIdAndType(authorId, RevenueType.WITHDRAWAL);
            int runningBalance = earned - withdrawn;

            List<Revenue> revenues = new ArrayList<>();
            int discountPerEp = discountAmount / episodeCount;
            int discountRem = discountAmount % episodeCount;
            for (int i = 0; i < episodeCount; i++) {
                Episode ep = unpurchasedEpisodes.get(i);
                int actualPrice = ep.getPointPrice() - discountPerEp - (i == 0 ? discountRem : 0);
                runningBalance += actualPrice;
                revenues.add(Revenue.create(authorId, ep.getId(), actualPrice, runningBalance, RevenueType.EPISODE_SALE));
            }
            revenueRepository.saveAll(revenues);
            log.info("[소설 전체 구매] 작가 수익 기록 authorId={} novelId={} 총금액={}P", authorId, novelId, finalPrice);
        });

        log.info("[소설 전체 구매] 구매 완료 userId={} novelId={} 회차수={} 최종금액={}P 잔액={}P",
                userId, novelId, unpurchasedEpisodes.size(), finalPrice, point.getBalance());

        // 7. 응답 생성
        return NovelBulkPurchaseResponse.of(
                novelId, unpurchasedEpisodes, discountRate, point.getBalance()
        );
    }

    /**
     * Episode 구매 가능 여부 검증
     */
    private void validatePurchasable(Episode episode) {
        // 무료 회차 체크
        if (episode.isFree()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_FREE_NO_PURCHASE);
        }

        // 발행 상태 체크
        if (episode.getStatus() != EpisodeStatus.PUBLISHED) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_AVAILABLE_FOR_PURCHASE);
        }

        // 삭제 여부 체크
        if (episode.isDeleted()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_AVAILABLE_FOR_PURCHASE);
        }
    }

    /**
     * 미구매 회차 목록 조회
     */
    private List<Episode> getUnpurchasedEpisodes(Long userId, Long novelId) {
        // 1. 발행된 유료 회차 목록 조회
        List<Episode> paidEpisodes = episodeRepository.findPublishedPaidEpisodesByNovelId(novelId);

        // 2. 이미 구매한 회차 ID 목록 조회
        List<Long> purchasedIds = pointHistoryRepository.findPurchasedEpisodeIds(
                userId, novelId, PointHistoryType.NOVEL
        );

        // 3. 미구매 회차 필터링
        return paidEpisodes.stream()
                .filter(ep -> !purchasedIds.contains(ep.getId()))
                .toList();
    }
}
