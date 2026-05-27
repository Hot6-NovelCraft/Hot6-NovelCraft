package com.example.hot6novelcraft.domain.search.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.domain.search.dto.IntegratedAuthorSearchResponse;
import com.example.hot6novelcraft.domain.search.dto.NovelSearchResponse;
import com.example.hot6novelcraft.domain.search.dto.TagGroupSearchResponse;
import com.example.hot6novelcraft.domain.search.repository.CustomSearchRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final CustomSearchRepository customSearchRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String NOVEL_SEARCH_RANK_KEY = "ranking:search:novel";
    private static final String TAG_SEARCH_RANK_KEY = "ranking:search:tag";
    private static final String HISTORY_KEY_PREFIX = "search:history:";

    /** ============ V1 ============
     1. 제목(소설) 검색
     2. 태그 검색
     3. 작가 통합 검색
     =================================== */
    public Page<NovelSearchResponse> searchNovelsV1(String keyword, Pageable pageable) {
        return customSearchRepository.searchNovelsByTitle(keyword, pageable, true);
    }

    public List<TagGroupSearchResponse> searchByTagsV1(List<String> tags) {
        return customSearchRepository.searchNovelsByTags(tags, true);
    }

    public IntegratedAuthorSearchResponse searchAuthorsV1(String keyword) {
        return customSearchRepository.searchByAuthorKeyword(keyword, true);
    }

    /** ============ V2 ============
     1. 제목(소설) 검색
     2. 태그 검색
     3. 작가 통합 검색
        - 비로그인 가능
        - 로그인 시 검색어 저장
     =================================== */
    public Page<NovelSearchResponse> searchNovels(String keyword, Pageable pageable, UserDetailsImpl userDetails) {
        // 성인 여부 판단
        boolean isAdult = userDetails != null && userDetails.getUser().isAdultVerificationValid();

        saveSearchHistoryIfLoggedIn(keyword, userDetails, NOVEL_SEARCH_RANK_KEY);
        return customSearchRepository.searchNovelsByTitle(keyword, pageable, isAdult);
    }

    public List<TagGroupSearchResponse> searchByTags(List<String> tags, UserDetailsImpl userDetails) {

        // 성인 여부 판단
        boolean isAdult = userDetails != null && userDetails.getUser().isAdultVerificationValid();
        if(tags.contains("ADULT") && !isAdult) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_ADULT_VERIFICATION_REQUIRED);
        }

        // 개별 태그들 각각 인기 랭킹에 +1
        for(String tag : tags) {
            saveSearchHistoryIfLoggedIn(tag, null, TAG_SEARCH_RANK_KEY);
        }

        // 내 검색 기록에는 "로맨스, 판타지" 묶어서 1개로 저장
        String keyword = String.join(",", tags);
        saveSearchHistoryIfLoggedIn(keyword, userDetails, null);

        return customSearchRepository.searchNovelsByTags(tags, isAdult);
    }

    public IntegratedAuthorSearchResponse searchAuthors(String keyword, UserDetailsImpl userDetails) {
        // 성인 여부 판단
        boolean isAdult = userDetails != null && userDetails.getUser().isAdultVerificationValid();

        saveSearchHistoryIfLoggedIn(keyword, userDetails, null);
        return customSearchRepository.searchByAuthorKeyword(keyword, isAdult);
    }

    /** =========== 조회 ============
     1. 인기 검색어 TOP 5 조회
     2. 인기 태그 TOP 5 조회
     3. 내 최근 검색어 전체 조회 (로그인 사용자만)
     =================================== */
    public List<String> getTopSearchKeywords() {
        Set<String> topKeywords = redisTemplate.opsForZSet().reverseRange(NOVEL_SEARCH_RANK_KEY, 0, 4);
        return topKeywords == null ? List.of() : topKeywords
                .stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    public List<String> getTopTagsKeywords() {
        Set<String> topKeywords = redisTemplate.opsForZSet().reverseRange(TAG_SEARCH_RANK_KEY, 0, 4);
        return topKeywords == null ? List.of() : topKeywords
                .stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    // 내 최근 검색어 전체 조회
    public List<String> getRecentSearchKeywords(Long userId) {
        String historyKey = HISTORY_KEY_PREFIX + userId;

        // 시간 역순으로 조회
        Set<String> recent = redisTemplate.opsForZSet().reverseRange(historyKey, 0, -1);
        return recent == null ? Collections.emptyList() : new ArrayList<>(recent);
    }

    /** ============ 통합 ==============
     인기 점수 증가 + 개인 히스토리 저장
     =================================== */
    private void saveSearchHistoryIfLoggedIn(String keyword, UserDetailsImpl userDetails, String redisKey) {
        if(keyword == null || keyword.trim().isEmpty()) {
            return;
        }

        try {
            // 중복 시 스코어만 갱신해 최신순으로 유지 -> ZSet 타임스탬프로 스코어 추가
            if (redisKey != null) {
                redisTemplate.opsForZSet().incrementScore(redisKey, keyword, 1);
            }

            // 개인 히스토리 저장
            if (userDetails != null) {
                Long userId = userDetails.getUser().getId();
                String historyKey = HISTORY_KEY_PREFIX + userId;

                // 중복 시 스코어만 갱신해 최신순 유지 -> ZSet 타임스탬프로 스코어 추가
                redisTemplate.opsForZSet().add(historyKey, keyword, System.currentTimeMillis());

                // 최근 10개만 유지 -> 오래된 것 삭제
                redisTemplate.opsForZSet().removeRange(historyKey, 0, -11);

                // 해당 키의 TTL이 설정되어 있지 않다면, 금일 00시까지 만료 시간 설정
                Long expire = redisTemplate.getExpire(historyKey);
                if (expire == null || expire <= 0) {
                    LocalDateTime midnight = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
                    Duration duration = Duration.between(LocalDateTime.now(), midnight);
                    redisTemplate.expire(historyKey, duration);
                }
            }
        } catch (RuntimeException e) {
                log.warn("[Redis 장애] 검색 저장을 건너뜁니다.");
        }
    }
}
