package com.example.hot6novelcraft.domain.admin.scheduler;

import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.admin.entity.AdminStatistics;
import com.example.hot6novelcraft.domain.admin.repository.AdminStatisticsRepository;
import com.example.hot6novelcraft.domain.admin.service.AdminCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j(topic = "AdminStatsScheduler")
@Component
@RequiredArgsConstructor
public class AdminStatsScheduler {

    private final AdminCacheService adminCacheService;
    private final AdminStatisticsRepository statisticRepository;
    private final RedisUtil redisUtil;

    private static final String STATISTICS_SCHEDULER_LOCK_KEY = "lock:admin:stats:daily:";

    @Scheduled(cron = "10 00 00 * * *")
    public void saveDailyStatistics() {
        //LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate yesterday = LocalDate.now();
        String lockKey = STATISTICS_SCHEDULER_LOCK_KEY + yesterday;

        // 1차 분산락 획득 시도
        if (!redisUtil.acquireLock(lockKey)) {
            log.info("[신규 통계 저장 스케쥴러] 다른 인스턴스에서 이미 마감 작업을 실행 중입니다. 저장 스킵: {}", yesterday);
            return;
        }

        try {
            log.info("[신규 통계 저장 스케쥴러] {} 일자 대시보드 통계 마감, 저장 시작", yesterday);

            // 2차 방어 멱등성 체크
            if (statisticRepository.existsByStatsDate(yesterday)) {
                log.warn("[신규 통계 저장 스케쥴러] {} 일자 통계 이미 존재, 저장 스킵", yesterday);
                return;
            }

            // Redis에서 오늘 23시 59분까지 모인 최종 카운트 가져오기
            Long finalUserCount = adminCacheService.getNewUsersByDate(yesterday);
            Long finalNovelCount = adminCacheService.getNewNovelsByDate(yesterday);
            Long finalMentoCount = adminCacheService.getNewMentosByDate(yesterday);

            // DB에 영구 보존
            AdminStatistics dailyStats = AdminStatistics.builder()
                    .statsDate(yesterday)
                    .newUserCount(finalUserCount)
                    .newNovelCount(finalNovelCount)
                    .newMentorCount(finalMentoCount)
                    .build();

            statisticRepository.save(dailyStats);
            log.info("[신규 통계 저장 스케쥴러] {} 일자 통계 저장 완료 (User:{}, Novel:{}", yesterday, finalUserCount, finalNovelCount);

        } catch (Exception e) {
            log.error("[신규 통계 저장 스케쥴러] 마감 저장 중 치명적 에러 발생, 일자: {}", yesterday, e);

        } finally {
            // 락 해제
            redisUtil.releaseLock(lockKey);
        }
    }
}
