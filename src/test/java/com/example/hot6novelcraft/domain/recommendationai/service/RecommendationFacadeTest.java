package com.example.hot6novelcraft.domain.recommendationai.service;

import com.example.hot6novelcraft.domain.novel.dto.response.NovelRankingResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.novel.service.NovelRankingService;
import com.example.hot6novelcraft.domain.recommendationAi.dto.RecommendationResponse;
import com.example.hot6novelcraft.domain.recommendationAi.dto.UserBehaviorSummary;
import com.example.hot6novelcraft.domain.recommendationAi.service.RecommendationFacade;
import com.example.hot6novelcraft.domain.recommendationAi.service.RecommendationService;
import com.example.hot6novelcraft.domain.recommendationAi.service.UserBehaviorCollectorService;
import com.example.hot6novelcraft.domain.recommendationAi.service.UserPreferenceAnalyzer;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationFacadeTest {

    @InjectMocks
    private RecommendationFacade recommendationFacade;

    @Mock private UserBehaviorCollectorService behaviorCollectorService;
    @Mock private RecommendationService recommendationService;
    @Mock private NovelRepository novelRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPreferenceAnalyzer preferenceAnalyzer;
    @Mock private NovelRankingService novelRankingService;

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private ZSetOperations<String, Object> zSetOperations;

    private final Long USER_ID = 1L;
    private final String CACHE_KEY = "recommendation::user:" + USER_ID;

    @BeforeEach
    void setUp() {
        // RedisTemplate의 opsForValue()가 호출되면 valueOperations 목 객체를 반환하도록 설정
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("맞춤 추천 - 캐시 HIT 시 AI 호출 없이 캐시 데이터를 즉시 반환한다")
    void getPersonalizedRecommendations_CacheHit() {
        // given
        RecommendationResponse cachedResponse = new RecommendationResponse(Collections.emptyList(), "PERSONALIZED");
        when(valueOperations.get(CACHE_KEY)).thenReturn(cachedResponse);

        // when
        RecommendationResponse response = recommendationFacade.getPersonalizedRecommendations(USER_ID);

        // then
        assertThat(response).isNotNull();
        assertThat(response.recommendationType()).isEqualTo("PERSONALIZED");

        // 캐시를 타면 하위 서비스들이 전혀 호출되지 않아야 함
        verify(behaviorCollectorService, never()).collect(anyLong());
        verify(recommendationService, never()).getPersonalizedRecommendations(any(), any(), any());
    }

    @Test
    @DisplayName("맞춤 추천 - 데이터 충분 시 PERSONALIZED AI 추천을 호출하고 결과를 캐싱한다")
    void getPersonalizedRecommendations_SufficientData_Success() {
        // given
        when(valueOperations.get(CACHE_KEY)).thenReturn(null); // 캐시 미스

        // 1. 행동 데이터 요약 모킹 (데이터가 존재하는 상태)
        UserBehaviorSummary behavior = new UserBehaviorSummary(
                List.of("소설A - FANTASY"), List.of("FANTASY"), List.of("판타지"), List.of("먼치킨"), List.of()
        );
        when(behaviorCollectorService.collect(USER_ID)).thenReturn(behavior);

        // 2. 선호 장르 분석 모킹
        Map<String, Integer> genreScore = Map.of("FANTASY", 5);
        when(preferenceAnalyzer.calculateGenrePreference(behavior)).thenReturn(genreScore);
        when(preferenceAnalyzer.getTopGenre(genreScore)).thenReturn(Optional.of("FANTASY"));

        // 3. 후보 소설 필터링 모킹 (NovelRepository)
        Novel mockNovel = mock(Novel.class);
        lenient().when(mockNovel.getId()).thenReturn(10L);
        lenient().when(mockNovel.getGenre()).thenReturn("FANTASY");

        when(novelRepository.findByGenreForRecommendation(eq("FANTASY"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mockNovel)));
        when(novelRepository.findExcludeGenreForRecommendation(eq("FANTASY"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // 4. AI 추천 결과 모킹 (소설 ID 10번 추천됨)
        when(recommendationService.getPersonalizedRecommendations(eq(behavior), eq(genreScore), anyList()))
                .thenReturn(List.of(10L));

        // 5. 🌟 핵심 수정 포인트: 커스텀 쿼리로 변경된 메서드 모킹 추가!
        // (getNovelsByIds 로직용)
        lenient().when(novelRepository.findActiveNovelsByIds(List.of(10L))).thenReturn(List.of(mockNovel));
        // (logRecommendation 로직용)
        lenient().when(novelRepository.findAllById(List.of(10L))).thenReturn(List.of(mockNovel));

        // when
        RecommendationResponse response = recommendationFacade.getPersonalizedRecommendations(USER_ID);

        // then
        assertThat(response.recommendationType()).isEqualTo("PERSONALIZED");
        assertThat(response.novels()).hasSize(1); // 이제 정상적으로 1개가 반환됩니다!

        // Redis에 저장되었는지 검증
        verify(valueOperations).set(eq(CACHE_KEY), eq(response), any(Duration.class));
    }

    @Test
    @DisplayName("맞춤 추천 - 행동 데이터 부족 시 TREND(트렌드) 추천으로 자동 Fallback 한다")
    void getPersonalizedRecommendations_InsufficientData_FallbackToTrend() {
        // given
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        // 행동 데이터가 텅 빈 상태 모킹
        UserBehaviorSummary emptyBehavior = new UserBehaviorSummary(
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(behaviorCollectorService.collect(USER_ID)).thenReturn(emptyBehavior);
        lenient().when(preferenceAnalyzer.calculateGenrePreference(emptyBehavior)).thenReturn(Collections.emptyMap());

        // 후보 소설 가져오기 모킹
        Novel mockNovel = mock(Novel.class);
        lenient().when(mockNovel.getId()).thenReturn(20L);
        lenient().when(mockNovel.getGenre()).thenReturn("FANTASY");

        // 🌟 1, 2단계가 적용되어야 여기서 에러가 안 납니다!
        lenient().when(novelRepository.findTop50ForRecommendation(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mockNovel)));

        // AI 트렌드 추천 결과 모킹
        when(recommendationService.getTrendRecommendations(anyList(), anyList()))
                .thenReturn(List.of(20L));

        // 중복 제거 및 안전망
        lenient().when(novelRepository.findAllById(Collections.emptyList())).thenReturn(Collections.emptyList());
        lenient().when(novelRepository.findAllById(List.of(20L))).thenReturn(List.of(mockNovel));

        // when
        RecommendationResponse response = recommendationFacade.getPersonalizedRecommendations(USER_ID);

        // then
        assertThat(response.recommendationType()).isEqualTo("TREND");
        verify(recommendationService, never()).getPersonalizedRecommendations(any(), any(), any());
        verify(recommendationService, times(1)).getTrendRecommendations(anyList(), anyList());
    }

    @Test
    @DisplayName("맞춤 추천 - AI가 빈 목록 반환 시 Redis 실시간 랭킹(FALLBACK)으로 대체한다")
    void getPersonalizedRecommendations_AiFail_FallbackToRedisRanking() {
        // given
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        UserBehaviorSummary behavior = new UserBehaviorSummary(List.of(), List.of(), List.of(), List.of(), List.of());
        when(behaviorCollectorService.collect(USER_ID)).thenReturn(behavior);

        // 🌟 핵심 수정 포인트: 파라미터로 any(Pageable.class)를 받고 빈 PageImpl을 반환하도록 수정
        lenient().when(novelRepository.findTop50ForRecommendation(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // AI가 빈 목록 반환
        when(recommendationService.getTrendRecommendations(anyList(), anyList())).thenReturn(Collections.emptyList());

        // Redis 랭킹 모킹
        NovelRankingResponse rankingResponse = new NovelRankingResponse(30, 1L, "커버", "장르", 100L,null); // 생성자 파라미터 타입에 맞게 수정 (필요시 확인)
        when(novelRankingService.getNovelRanking("realtime")).thenReturn(List.of(rankingResponse));

        Novel mockNovel = mock(Novel.class);
        lenient().when(mockNovel.getId()).thenReturn(30L);
        lenient().when(mockNovel.getGenre()).thenReturn("FANTASY"); // NPE 방지용

        when(novelRepository.findAllById(List.of(30L))).thenReturn(List.of(mockNovel));

        // logRecommendation을 통과하기 위해 빈 배열 조회에 대한 모킹 추가
        lenient().when(novelRepository.findAllById(Collections.emptyList())).thenReturn(Collections.emptyList());
        lenient().when(novelRepository.findAllById(List.of(30L))).thenReturn(List.of(mockNovel));

        // when
        RecommendationResponse response = recommendationFacade.getPersonalizedRecommendations(USER_ID);

        // then
        assertThat(response.recommendationType()).isEqualTo("FALLBACK"); // 폴백 작동 확인
        verify(novelRankingService, times(1)).getNovelRanking("realtime");
    }
}
