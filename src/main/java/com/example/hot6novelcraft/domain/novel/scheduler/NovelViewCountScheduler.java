package com.example.hot6novelcraft.domain.novel.scheduler;

import com.example.hot6novelcraft.domain.episode.service.EpisodeCacheService;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NovelViewCountScheduler {

    private final EpisodeCacheService episodeCacheService;
    private final NovelRepository novelRepository;

    // 5분마다 Redis 조회수 -> DB 반영
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void syncViewCount() {
        Set<String> keys = episodeCacheService.getAllViewCountKeys();

        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            // novel_view_count::1 에서 novelId 추출
            Long novelId = Long.parseLong(key.replace("novel_view_count::", ""));
            long count = episodeCacheService.getViewCount(novelId);

            if (count > 0) {
                novelRepository.incrementViewCountBy(novelId, count);
                episodeCacheService.resetViewCount(novelId);
                log.info("[조회수 동기화] novelId={} count={}", novelId, count);
            }
        }
    }
}
