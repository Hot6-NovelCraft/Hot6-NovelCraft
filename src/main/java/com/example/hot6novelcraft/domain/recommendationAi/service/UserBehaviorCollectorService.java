package com.example.hot6novelcraft.domain.recommendationAi.service;

import com.example.hot6novelcraft.domain.library.repository.LibraryRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.recommendationAi.dto.UserBehaviorSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j(topic = "RecommendUserBehaviorCollector")
@Component
@RequiredArgsConstructor
public class UserBehaviorCollectorService {

    private final PointHistoryRepository pointHistoryRepository;
    private final LibraryRepository libraryRepository;
    private final NovelRepository novelRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String HISTORY_KEY_PREFIX = "search:history:";
    private static final String TAG_SEARCH_RANK_KEY = "ranking:search:tag";

    /**
     * 사용자 행동 데이터 수집 후 AI 입력용 요약으로 변환
     * @param userId 로그인한 사용자 ID
     * @return AI에게 넘길 행동
     */
    public UserBehaviorSummary collect(Long userId) {
        return new UserBehaviorSummary(
                collectRecentReadNovels(userId)
                , collectLibraryGenres(userId)
                , collectRecentKeywords(userId)
                , collectLikedTags(userId)
                , collectPopularTags()
        );
    }

    // 최근 구매한 소설 제목 + 장르 수집 (최근 10개, pointHistory에서 Novel 타입만 조회)
    private List<String> collectRecentReadNovels(Long userId) {
        try {
            List<Long> novelIds = pointHistoryRepository
                    .findRecentPurchasedNovelIds(userId, PointHistoryType.NOVEL, PageRequest.of(0, 10))
                    .getContent();

            if (novelIds.isEmpty()) {
                return Collections.emptyList();
            }

            // novelId로 제목+장르 조회해서 "제목-장르" 형태로 변환
            return novelRepository.findAllById(novelIds)
                    .stream()
                    .distinct()
                    .limit(10)
                    .map(novel -> novel.getTitle() + " - " + novel.getGenre())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[AI 추천] 최근 읽은 소설 수집 실패 userId: {}", userId, e);
            return Collections.emptyList();
        }
    }

    // 서재에 있는 소설들 장르 수집 + 장르 빈도로 선호 장르 파악
    private List<String> collectLibraryGenres(Long userId) {
        try {
            return libraryRepository.findByUserId(userId)
                    .stream()
                    // Library에는 장르가 없어서 소설로 조회
                    .map(library -> novelRepository.findById(library.getNovelId()).orElse(null))
                    .filter(novel -> novel != null && !novel.isDeleted())
                    .map(novel -> novel.getGenre())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[AI 추천] 서재 장르 수집 실패 userId: {}", userId, e);
            return Collections.emptyList();
        }
    }

    // Redis 최근 검색어 수집 (로그인 사용자)
    private List<String> collectRecentKeywords(Long userId) {
        try {
            String historyKey = HISTORY_KEY_PREFIX + userId;
            var recent = redisTemplate.opsForZSet().reverseRange(historyKey, 0, 9);
            return recent == null
                    ? Collections.emptyList()
                    : recent.stream().toList();
        } catch (Exception e) {
            log.warn("[AI 추천] 최근 검색어 수집 실패 userId: {}", userId, e);
            return Collections.emptyList();
        }
    }

    // 좋아요한 회차 소설 태그 수집 (로그인 사용자)
    private List<String> collectLikedTags(Long userId) {
        try {
            return novelRepository.findTagsByUserLikedEpisodes(userId);
        } catch (Exception e) {
            log.warn("[AI 추천] 좋아요 태그 수집 실패 userId: {}", userId, e);
            return Collections.emptyList();
        }
    }

    // 플랫폼 인기 태그 Top 5 수집 (비로그인 사용자 / 로그인 사용자 콜드스타트로 대응)
    private List<String> collectPopularTags() {
        try {
            var topTags = redisTemplate.opsForZSet().reverseRange(TAG_SEARCH_RANK_KEY, 0, 4);
            return topTags == null
                    ? Collections.emptyList()
                    : topTags.stream().toList();
        } catch (Exception e) {
            log.warn("[AI 추천] 좋아요 태그 수집 실패", e);
            return Collections.emptyList();
        }
    }
}
