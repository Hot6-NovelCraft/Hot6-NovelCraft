package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeCommentListResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodeCommentCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String COMMENT_KEY_PREFIX = "comment:v2::";
    private static final Duration TTL = Duration.ofMinutes(30);

    // 댓글 캐시 조회
    public PageResponse<EpisodeCommentListResponse> getCommentCache(Long episodeId, int page) {
        String key = COMMENT_KEY_PREFIX + episodeId + "::" + page;
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached == null) return null;

        try {
            return objectMapper.readValue(
                    cached.toString(),
                    new TypeReference<PageResponse<EpisodeCommentListResponse>>() {}
            );
        } catch (Exception e) {
            redisTemplate.delete(key);
            log.warn("[Comment Cache] 역직렬화 실패 key={}", key, e);
            return null;
        }
    }

    // 댓글 캐시 저장
    public void saveCommentCache(Long episodeId, int page,
                                 PageResponse<EpisodeCommentListResponse> response) {
        String key = COMMENT_KEY_PREFIX + episodeId + "::" + page;
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, TTL);
            log.debug("[Comment Cache] SAVE key={}", key);
        } catch (Exception e) {
            log.warn("[Comment Cache] 직렬화 실패: {}", e.getMessage());
        }
    }

    // 댓글 캐시 무효화 (작성,삭제 시)
    public void evictCommentCache(Long episodeId) {
        String pattern = COMMENT_KEY_PREFIX + episodeId + "::*";
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                redisTemplate.delete(cursor.next());
            }
            log.debug("[Comment Cache] EVICT episodeId={}", episodeId);
        } catch (Exception e) {
            log.warn("[Comment Cache] 무효화 실패: {}", e.getMessage());
        }
    }
}