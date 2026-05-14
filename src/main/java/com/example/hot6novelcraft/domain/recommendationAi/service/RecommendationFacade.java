package com.example.hot6novelcraft.domain.recommendationAi.service;

import com.example.hot6novelcraft.domain.novel.dto.response.NovelListResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelRankingResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.novel.service.NovelRankingService;
import com.example.hot6novelcraft.domain.recommendationAi.dto.NovelSummaryForAi;
import com.example.hot6novelcraft.domain.recommendationAi.dto.RecommendationResponse;
import com.example.hot6novelcraft.domain.recommendationAi.dto.UserBehaviorSummary;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j(topic = "RecommendationFacade")
@Service
@RequiredArgsConstructor
public class RecommendationFacade {

    private final UserBehaviorCollectorService behaviorCollectorService;
    private final RecommendationService recommendationService;
    private final NovelRepository novelRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final UserPreferenceAnalyzer preferenceAnalyzer;

    // 캐시 설정 - API 비용 절약 (같은 유저가 30분 내 재요청 시 캐시 반환)
    private static final String CACHE_KEY_PREFIX = "recommendation::";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private final NovelRankingService novelRankingService;

    /**
     * ==== 로그인 사용자 맞춤 추천 ====
     * 캐시 HIT -> 즉시 반환
     * 캐시 MISS -> AI 호출 후 캐시 저장
     * ===================================
     */
    @SuppressWarnings("unchecked")
    public RecommendationResponse getPersonalizedRecommendations(Long userId) {

        // 캐시 확인 (역직렬화 실패 시 재생성)
        String cacheKey = CACHE_KEY_PREFIX + "user:" + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached instanceof RecommendationResponse response) {
            log.debug("[AI 추천] 캐시 HIT userId: {}", userId);
            return response;
        }

        // AI 성능 측정 시작
        long start = System.currentTimeMillis();

        // 사용자 행동 수집
        UserBehaviorSummary behavior = behaviorCollectorService.collect(userId);

        // 선호 장르 사전 계산
        Map<String, Integer> genrePreference = preferenceAnalyzer.calculateGenrePreference(behavior);

        // 선호 장르 비율로 후보 필터링
        List<NovelSummaryForAi> candidates = behavior.inInsufficientData()
                ? getCandidateNovels() // 데이터 없으면 전체 후보로 필터
                : getFilteredCandidates(genrePreference); // 데이터 있으면 선호 장르 기반 필터

        // 행동 데이터 부족 시 트렌드 추천 출력 (fallback)
        List<Long> recommendedIds;
        String type;

        if (behavior.inInsufficientData()) {
            log.info("[AI 추천] 행동 데이터 부족 -> 트렌드 추천으로 전환 userId: {}", userId);

            recommendedIds = recommendationService.getTrendRecommendations(behavior.popularTags(), candidates);
            type = "TREND";

        } else {
            recommendedIds = recommendationService.getPersonalizedRecommendations(behavior, genrePreference, candidates);
            type = "PERSONALIZED";
        }

        long elapsed = System.currentTimeMillis() - start;

        // 성능 + 정합성 한 줄 로그 동시 기록
        logRecommendation(userId, type, elapsed, genrePreference, recommendedIds);

        // AI가 빈 목록 반환 시 인기 소설로 최종 출력 (fallback)
        if (recommendedIds.isEmpty()) {
            log.warn("[AI 추천] 추천 실패 -> 인기 소설로 대체 userId: {}", userId);

            RecommendationResponse response = getFallbackRecommendation();
            redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
            return response;
        }

        // 선호 장르 정보를 behavior이랑 같이 AI 전달
        List<NovelListResponse> novels = getNovelsByIds(recommendedIds);
        RecommendationResponse response = new RecommendationResponse(novels, type);

        // 캐시 저장
        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);

        return response;
    }

        // 비로그인 트렌드 추천 - TTL 1시간 (모든 비로그인 사용자 공용 캐시)
        public RecommendationResponse getTrendRecommendation () {
            String cacheKey = CACHE_KEY_PREFIX + "trend";
            Object cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached instanceof RecommendationResponse response

            ) {
                log.debug("[AI 추천] 트랜드 캐시 HIT");
                return response;
            }

            // Redis 인기 태그 수집
            List<String> popularTags = getPopularTags();
            List<NovelSummaryForAi> candidates = getCandidateNovels();

            List<Long> recommendedIsd = recommendationService.getTrendRecommendations(popularTags, candidates);

            if (recommendedIsd.isEmpty()) {
                return getFallbackRecommendation();
            }

            List<NovelListResponse> novels = getNovelsByIds(recommendedIsd);
            RecommendationResponse response = new RecommendationResponse(novels, "TREND");

            // 비로그인 공용 캐시
            redisTemplate.opsForValue().set(cacheKey, response, Duration.ofHours(1));

            return response;
        }

            /** ======= 공용 메소드 =======  */

            // 선호 장르 비율로 후보 소설 구성 -> 선호 장르 60% + 나머지 장르 40% 섞어서
            private List<NovelSummaryForAi> getFilteredCandidates (
                    Map < String, Integer > genrePreference){

                // 선호 장르 없으면 전체 후보 반환
                if (genrePreference.isEmpty()) {
                    return getCandidateNovels();
                }

                Optional<String> topGenre = preferenceAnalyzer.getTopGenre(genrePreference);

                if (topGenre.isEmpty()) {
                    return getCandidateNovels();
                }

                String preferredGenre = topGenre.get();

                // 선호 장르 소설 30개
                List<NovelSummaryForAi> preferred = novelRepository.findByGenreForRecommendation(preferredGenre, PageRequest.of(0, 30))
                        .getContent()
                        .stream()
                        .map(n -> new NovelSummaryForAi(
                                n.getId(), n.getCoverImageUrl(), n.getTitle(), n.getGenre(), n.getTags(), n.getAuthorId()))
                        .collect(Collectors.toList());

                // 나머지 장르 소설 20개 (다양성 확보)
                List<NovelSummaryForAi> others = novelRepository.findExcludeGenreForRecommendation(preferredGenre, PageRequest.of(0, 20))
                        .getContent()
                        .stream()
                        .map(n -> new NovelSummaryForAi(
                                n.getId(), n.getCoverImageUrl(), n.getTitle(), n.getGenre(), n.getTags(), n.getAuthorId()))
                        .collect(Collectors.toList());

                // 합쳐서 반환
                List<NovelSummaryForAi> candidates = new ArrayList<>(preferred);
                candidates.addAll(others);

                log.info("[AI 추천] 후보 구성 - 선호장르 ({}) {}개 + 기타 {}개", preferredGenre, preferred.size(), others.size());

                return candidates;
            }

            // AI에게 넘길 후보 소설 목록 조회 - 연재(ongoing), 완결(completed) 상태만 최신순 50개로 제한
            private List<NovelSummaryForAi> getCandidateNovels () {
                return novelRepository.findTop50ForRecommendation(PageRequest.of(0, 50))
                        .stream()
                        .map(n -> new NovelSummaryForAi(
                                n.getId()
                                , n.getCoverImageUrl()
                                , n.getTitle()
                                , n.getGenre()
                                , n.getTags()
                                , n.getAuthorId()
                        ))
                        .collect(Collectors.toList());
            }

            // 추천 ID 순서 유지하면서 소설 목록 조회
            private List<NovelListResponse> getNovelsByIds (List < Long > novelIds) {

                // AI가 빈 결과를 줬을 때 바로 DB 결과가 아닌 빈 리스트 반환
                if (novelIds == null || novelIds.isEmpty()) {
                    return Collections.emptyList();
                }

                Map<Long, Novel> novelMap = novelRepository.findActiveNovelsByIds(novelIds)
                        .stream()
                        .collect(Collectors.toMap(Novel::getId, novel -> novel));

                return novelIds.stream()
                        .filter(novelMap::containsKey)
                        .map(id -> {
                            Novel n = novelMap.get(id);
                            return NovelListResponse.of(
                                    n.getId()
                                    , n.getTitle()
                                    , n.getGenre()
                                    , n.getTags()
                                    , n.getStatus()
                                    , n.getCoverImageUrl()
                                    , n.getViewCount()
                                    , n.getBookmarkCount()
                                    , getAuthorNickname(n.getAuthorId())
                            );
                        })
                        .collect(Collectors.toList());
            }

            // AI 응답 실패 시, 최종 안전망 - Redis 인기 랭킹 소설로 대체
            private RecommendationResponse getFallbackRecommendation () {

                // Redis 랭킹에서 상위 소설 ID 가져오기
                List<NovelRankingResponse> ranking =
                        novelRankingService.getNovelRanking("realtime");

                // Redis가 비어있으면 주간으로 재시도
                if (ranking.isEmpty()) {
                    ranking = novelRankingService.getNovelRanking("weekly");
                }

                // Redis도 비어있으면 DB에서 직접 조회
                if (ranking.isEmpty()) {
                    log.warn("[AI 추천] Redis 랭킹 없음 - DB 직접 조회");

                    return getFallbackFromDb();
                }

                // 랭킹 ID 순서유지하면서 Novel 상세 조회
                List<Long> novelIds = ranking.stream()
                        .map(NovelRankingResponse::novelId)
                        .collect(Collectors.toList());

                Map<Long, Novel> novelMap = novelRepository.findAllById(novelIds)
                        .stream()
                        .collect(Collectors.toMap(Novel::getId, novel -> novel));

                // 랭킹 순서 유지하면서 NovelListResponse 변환
                List<NovelListResponse> novels = novelIds.stream()
                        .filter(novelMap::containsKey)
                        .map(id -> {
                            Novel n = novelMap.get(id);

                            return NovelListResponse.of(
                                    n.getId()
                                    , n.getTitle()
                                    , n.getGenre()
                                    , n.getTags()
                                    , n.getStatus()
                                    , n.getCoverImageUrl()
                                    , n.getViewCount()
                                    , n.getBookmarkCount()
                                    , getAuthorNickname(n.getAuthorId())
                            );
                        })
                        .collect(Collectors.toList());

                return new RecommendationResponse(novels, "FALLBACK");
            }

            // Redis에도 없을 때 DB 조회
            private RecommendationResponse getFallbackFromDb () {
                List<Novel> popular = novelRepository.findFallbackNovels(PageRequest.of(0, 10)).getContent();

                List<NovelListResponse> novels = popular.stream()
                        .map(n -> NovelListResponse.of(
                                n.getId()
                                , n.getTitle()
                                , n.getGenre()
                                , n.getTags()
                                , n.getStatus()
                                , n.getCoverImageUrl()
                                , n.getViewCount()
                                , n.getBookmarkCount()
                                , getAuthorNickname(n.getAuthorId())
                        ))
                        .collect(Collectors.toList());

                return new RecommendationResponse(novels, "FALLBACK");
            }

            private List<String> getPopularTags () {
                var tags = stringRedisTemplate.opsForZSet().reverseRange("ranking:search:tag", 0, 4);

                return tags == null ?
                        Collections.emptyList()
                        : tags.stream()
                        .collect(Collectors.toList());
            }

            private String getAuthorNickname (Long authorId) {

                if (authorId == null) {
                    return "알 수 없는 작가";
                }

                return userRepository.findById(authorId)
                        .map(user -> user.getNickname())
                        .orElse("알 수 없는 작가");
            }

            /** ===== 성능 + 정합성 통합 로그 확인 =====
             * 소요시간 : AI가 다른 API에 방해되는지 확인
             * 선호장르 vs 추천 졀과 : 의도한 대로 나왔는지 확인 (정합성)
             ===================================== */
            private void logRecommendation (
                    Long userId
                    , String type
            ,long elapsedMs
            ,Map<String, Integer> genrePreference
            ,List<Long> recommendedIds
        ){
                // 추천 소설 실제 장르 조회
                List<Novel> recommendedNovels = novelRepository.findAllById(recommendedIds);

                // 추천 결과 장르 빈도 계산
                Map<String, Long> resultGenreCount = recommendedNovels.stream()
                        .collect(Collectors.groupingBy(Novel::getGenre, Collectors.counting()));

                // 선호 장르 1위 추출
                String topPreferredGenre = genrePreference.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("없음");

                // 추천 결과 중 선호 장르 비율 계산
                long preferredCount = resultGenreCount.getOrDefault(topPreferredGenre, 0L);
                int total = recommendedIds.size();
                double preferredRatio = total == 0 ? 0 : (double) preferredCount / total * 100;

                // 한 줄로 모든 정보 기록 -> grep으로 쉽게 필터링
                log.info("[AI추천결과] userId: {}, type: {} 소요: {}ms, 추천수: {}, 선호장르: {}, 선호장르비율: {}%, 장르분포: {}",
                        userId, type, elapsedMs, total, topPreferredGenre, preferredRatio, resultGenreCount);
            }
        }
