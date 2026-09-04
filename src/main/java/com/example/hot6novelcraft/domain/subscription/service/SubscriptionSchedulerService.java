package com.example.hot6novelcraft.domain.subscription.service;

import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.example.hot6novelcraft.domain.subscription.entity.enums.SubscriptionStatus;
import com.example.hot6novelcraft.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSchedulerService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    /**
     * 매일 자정 실행 (KST 기준) - 정기 청구
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void processMonthlyBilling() {
        log.info("[구독 스케줄러] 정기 청구 시작");

        LocalDateTime now = LocalDateTime.now();

        // nextBillingAt이 오늘 또는 지난 ACTIVE 구독 조회
        List<Subscription> dueSubscriptions = subscriptionRepository
                .findAllBySubscriptionStatusAndNextBillingAtBefore(
                        SubscriptionStatus.ACTIVE,
                        now.plusDays(1)
                );

        log.info("[구독 스케줄러] 청구 대상: {}건", dueSubscriptions.size());

        for (Subscription subscription : dueSubscriptions) {
            try {
                subscriptionService.processBillingForSubscription(subscription);
            } catch (Exception e) {
                log.error("[구독 스케줄러] 청구 실패 subscriptionId={}",
                        subscription.getId(), e);
            }
        }

        log.info("[구독 스케줄러] 정기 청구 완료");
    }

    /**
     * 매일 새벽 2시 실행 (KST 기준) - PENDING 구독 자동 삭제
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void cleanUpPendingSubscriptions() {
        log.info("[구독 스케줄러] PENDING 구독 정리 시작");

        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(1);  // 24시간 전

        // 24시간 이상 PENDING 상태인 구독 조회
        List<Subscription> expiredSubscriptions = subscriptionRepository
                .findAllBySubscriptionStatusAndCreatedAtBefore(
                        SubscriptionStatus.PENDING,
                        cutoffTime
                );

        log.info("[구독 스케줄러] 삭제 대상: {}건", expiredSubscriptions.size());

        int deletedCount = 0;
        for (Subscription subscription : expiredSubscriptions) {
            try {
                subscriptionRepository.delete(subscription);
                deletedCount++;
            } catch (Exception e) {
                log.error("[구독 스케줄러] 삭제 실패 subscriptionId={}",
                        subscription.getId(), e);
            }
        }

        log.info("[구독 스케줄러] PENDING 구독 정리 완료 (삭제: {}건)", deletedCount);
    }
}
