package com.example.hot6novelcraft.domain.exchange.service;

import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueStatisticsResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueStatisticsResponse.RevenueStatisticsItem;
import com.example.hot6novelcraft.domain.exchange.entity.enums.StatisticsPeriod;
import com.example.hot6novelcraft.domain.exchange.repository.RevenueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private static final String STATISTICS_KEY_PREFIX = "revenue:statistics:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RevenueRepository revenueRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 수익 분석 통계 조회
     * - 월별/주별 수익 집계
     * - Redis 캐싱 (TTL 1시간)
     */
    public RevenueStatisticsResponse getStatistics(Long authorId, StatisticsPeriod period, Integer year) {
        String cacheKey = STATISTICS_KEY_PREFIX + authorId + ":" + period + ":" + year;

        // Redis 캐시 조회 (JSON 문자열로 저장/역직렬화)
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof String jsonStr) {
                log.debug("통계 캐시 히트 - authorId: {}, period: {}, year: {}", authorId, period, year);
                return objectMapper.readValue(jsonStr, RevenueStatisticsResponse.class);
            }
        } catch (Exception e) {
            log.warn("통계 캐시 역직렬화 실패, DB 조회로 전환 - authorId: {}", authorId, e);
        }

        // QueryDSL 집계 쿼리
        List<RevenueStatisticsItem> items = revenueRepository.findStatistics(authorId, period, year);

        // 전체 합계
        Integer totalAmount = items.stream()
                .mapToInt(RevenueStatisticsItem::totalAmount)
                .sum();

        RevenueStatisticsResponse response = RevenueStatisticsResponse.of(
                period.name(), year, totalAmount, items
        );

        // Redis 캐시 저장 (JSON 문자열로 직렬화하여 저장 - Record 클래스 타입 정보 보존)
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
            log.debug("통계 캐시 저장 - authorId: {}, period: {}, year: {}", authorId, period, year);
        } catch (Exception e) {
            log.warn("통계 캐시 저장 실패 - authorId: {}", authorId);
        }

        return response;
    }

    /**
     * 통계 캐시 무효화
     * - 수익 발생/환전 시 호출
     */
    public void evictStatisticsCache(Long authorId) {
        String pattern = STATISTICS_KEY_PREFIX + authorId + ":*";
        try (var cursor = redisTemplate.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(pattern).count(100).build())) {
            var keys = cursor.stream().collect(java.util.stream.Collectors.toSet());
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("통계 캐시 무효화 - authorId: {}, 삭제 건수: {}", authorId, keys.size());
            }
        }
    }
}