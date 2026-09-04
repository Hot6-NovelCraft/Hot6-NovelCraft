package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.domain.episode.dto.cache.EpisodeContentCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodeCacheService {

    private static final int HOT_THRESHOLD = 1; // K6 테스트용 기존50

    private static final String NOVEL_VIEW_KEY_PREFIX = "novel_view::";

    private static final String HOT_KEY_PREFIX = "novel_hot::";
    private static final String EPISODE_CONTENT_KEY_PREFIX = "episode_content::";

    private static final String REALTIME_RANKING_KEY = "ranking:novel:realtime";
    private static final String WEEKLY_RANKING_KEY = "ranking:novel:weekly";
    private static final String NOVEL_VIEW_COUNT_KEY_PREFIX = "novel_view_count::";


    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;


    // 조회수 어뷰징 체크 (true = 첫 조회, false = 중복 조회)
    public boolean isFirstView(Long userId, Long novelId) {
        String viewKey = NOVEL_VIEW_KEY_PREFIX + userId + "::" + novelId;

        Boolean isFirst = redisTemplate.opsForValue()
                .setIfAbsent(viewKey, "1", Duration.ofHours(1));

        return Boolean.TRUE.equals(isFirst);
    }

    // 회차 어뷰징 체크
    public boolean isFirstEpisodeView(Long userId, Long episodeId) {
        String key = "episode_view::" + userId + "::" + episodeId;
        Boolean isFirst = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofHours(1));
        return Boolean.TRUE.equals(isFirst);
    }


    // 최근 1시간 조회수 증가 및 반환
    public long increaseHotKeyCount(Long novelId) {
        String hotKey = HOT_KEY_PREFIX + novelId;

        Long count = redisTemplate.opsForValue().increment(hotKey);

        // 처음 생성된 경우 TTL 1시간 설정
        if (count != null && count == 1) {
            redisTemplate.expire(hotKey, Duration.ofHours(1));
        }

        return count != null ? count : 0L;
    }

    /**
     * 실시간 및 주간 랭킹 ZSet 점수 (조회수에 따른) 증가
     * 어뷰징 체크 통과 시에만 호출
     * 서하나
     */
    public void increaseRankingScore(Long novelId) {

        String stringNovelId = String.valueOf(novelId);

        try {
            redisTemplate.opsForZSet().incrementScore(REALTIME_RANKING_KEY, stringNovelId, 1);
            redisTemplate.opsForZSet().incrementScore(WEEKLY_RANKING_KEY, stringNovelId, 1);
            log.debug("[랭킹 점수 증가] novelId: {}, 실시간/주간 랭킹 +1", novelId);
        } catch (RuntimeException e) {
            log.warn("[랭킹 점수 증가 실패] novelIdL {}", novelId, e);
        }
    }

    // 인기작 여부 판별
    public boolean isHotNovel(long recentViews) {
        return recentViews >= HOT_THRESHOLD;
    }

    // 본문 캐시 조회 (단건)
    public EpisodeContentCache getContentCache(Long episodeId) {
        String key = EPISODE_CONTENT_KEY_PREFIX + episodeId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            log.debug("[Content Cache MISS] {}", key);
            return null;
        }
        try {
            log.debug("[Content Cache HIT] {}", key);
            return objectMapper.readValue(cached.toString(), EpisodeContentCache.class);
        } catch (JsonProcessingException e) {
            log.error("[Content Cache 역직렬화 실패] {}", key, e);
            return null;
        }
    }

    // 본문 캐시 저장 (TTL 30분)
    public void saveContentCache(Long episodeId, EpisodeContentCache content) {
        String key = EPISODE_CONTENT_KEY_PREFIX + episodeId;
        try {
            String json = objectMapper.writeValueAsString(content);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(30));
        } catch (JsonProcessingException e) {
            log.error("[Content Cache 직렬화 실패] {}", key, e);
        }
    }

    // 본문 캐시 무효화
    public void evictContentCache(Long episodeId) {
        String key = EPISODE_CONTENT_KEY_PREFIX + episodeId;
        redisTemplate.delete(key);
    }


    // Redis에 소설 조회수 증가
    public void increaseViewCount(Long novelId) {
        String key = NOVEL_VIEW_COUNT_KEY_PREFIX + novelId;
        redisTemplate.opsForValue().increment(key);
    }

    // Redis 일일 조회수
    public void increaseEpisodeDailyViewCount(Long episodeId) {
        String key = "episode_daily_view_count::" + episodeId + "::"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
    }

    // 회차 오늘 조회수 조회 (통계용)
    public long getEpisodeDailyViewCount(Long episodeId) {
        String key = "episode_daily_view_count::" + episodeId + "::"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Object count = redisTemplate.opsForValue().get(key);
        if (count == null) return 0L;
        try {
            return Long.parseLong(count.toString());
        } catch (NumberFormatException e) {
            log.warn("[회차 일일 조회수 파싱 실패] key: {}, value: {}", key, count);
            return 0L;
        }
    }

    // Redis에 저장된 조회수 조회
    public long getViewCount(Long novelId) {
        String key = NOVEL_VIEW_COUNT_KEY_PREFIX + novelId;
        Object count = redisTemplate.opsForValue().get(key);
        return count != null ? Long.parseLong(count.toString()) : 0L;
    }

    // Redis 조회수 초기화
    public void resetViewCount(Long novelId) {
        String key = NOVEL_VIEW_COUNT_KEY_PREFIX + novelId;
        redisTemplate.delete(key);
    }

    // 모든 소설 조회수 키 조회
    public Set<String> getAllViewCountKeys() {
        return redisTemplate.keys(NOVEL_VIEW_COUNT_KEY_PREFIX + "*");
    }
}