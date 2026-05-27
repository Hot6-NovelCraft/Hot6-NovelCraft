package com.example.hot6novelcraft.domain.search.service;

import com.example.hot6novelcraft.domain.search.dto.IntegratedAuthorSearchResponse;
import com.example.hot6novelcraft.domain.search.dto.NovelSearchResponse;
import com.example.hot6novelcraft.domain.search.dto.TagGroupSearchResponse;
import com.example.hot6novelcraft.domain.search.repository.CustomSearchRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.search.dto.AuthorSearchResponse;
import com.example.hot6novelcraft.domain.search.dto.NovelSimpleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SearchServiceTest {

    @InjectMocks
    private SearchService searchService;

    @Mock
    private CustomSearchRepository customSearchRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private Pageable pageable;
    private UserDetailsImpl loggedInUser;
    private UserDetailsImpl loggedInAdultUser;
    private UserDetailsImpl anonymousUser;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);

        // 1. 성인 유저
        User adultUser = mock(User.class);
        given(adultUser.getId()).willReturn(1L);
        given(adultUser.isAdultVerificationValid()).willReturn(true); // 성인 인증 완료 상태
        loggedInAdultUser = mock(UserDetailsImpl.class);
        given(loggedInAdultUser.getUser()).willReturn(adultUser);

        // 2. 미성년 유저
        User minorUser = mock(User.class);
        given(minorUser.getId()).willReturn(2L);
        given(minorUser.isAdultVerificationValid()).willReturn(false); // 미성년 혹은 인증 만료
        loggedInUser = mock(UserDetailsImpl.class);
        given(loggedInUser.getUser()).willReturn(minorUser);

        anonymousUser = null;
    }

    @Nested
    @DisplayName("V1 - Redis 없이 DB 검색")
    class V1Test {

        @Test
        @DisplayName("V1 소설 제목 검색 - Redis 저장 없이 결과만 반환")
        void searchNovelsV1_success() {
            List<NovelSearchResponse> novels = List.of(
                    new NovelSearchResponse(1L,"cover.jpg", "바다가 보이는 카페", "바다작가", "HEALING"),
                    new NovelSearchResponse(null, null,"바다 위의 던전", "바다작가", "FANTASY")
            );
            Page<NovelSearchResponse> mockPage = new PageImpl<>(novels, pageable, 2);
            given(customSearchRepository.searchNovelsByTitle("바다", pageable, false)).willReturn(mockPage);

            Page<NovelSearchResponse> result = searchService.searchNovelsV1("바다", pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).title()).isEqualTo("바다가 보이는 카페");
            verify(redisTemplate, never()).opsForZSet();
        }

        @Test
        @DisplayName("V1 태그 검색 - 태그별 그룹핑 반환, Redis 저장 없음")
        void searchByTagsV1_success() {
            List<String> tags = List.of("MUNCHKIN", "DUNGEON");
            List<TagGroupSearchResponse> mockResult = List.of(
                    new TagGroupSearchResponse("MUNCHKIN", List.of(
                            new NovelSimpleResponse("먼치킨 바다왕", "바다작가")
                    )),
                    new TagGroupSearchResponse("DUNGEON", List.of(
                            new NovelSimpleResponse("바다 위의 던전", "바다작가")
                    ))
            );
            given(customSearchRepository.searchNovelsByTags(tags, false)).willReturn(mockResult);

            List<TagGroupSearchResponse> result = searchService.searchByTagsV1(tags);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).tag()).isEqualTo("MUNCHKIN");
            assertThat(result.get(1).tag()).isEqualTo("DUNGEON");
            verify(redisTemplate, never()).opsForZSet();
        }

        @Test
        @DisplayName("V1 작가 검색 - 통합 결과 반환, Redis 저장 없음")
        void searchAuthorsV1_success() {
            IntegratedAuthorSearchResponse mockResult = new IntegratedAuthorSearchResponse(
                    List.of(new AuthorSearchResponse(1L, "백산", "판타지 작가", List.of())),
                    List.of(new NovelSimpleResponse("백산의 이세계 모험", "백산"))
            );
            given(customSearchRepository.searchByAuthorKeyword("백산", false)).willReturn(mockResult);

            IntegratedAuthorSearchResponse result = searchService.searchAuthorsV1("백산");

            assertThat(result.matchingAuthors()).hasSize(1);
            assertThat(result.matchingAuthors().get(0).nickname()).isEqualTo("백산");
            assertThat(result.matchingNovels()).hasSize(1);
            verify(redisTemplate, never()).opsForZSet();
        }
    }

    @Nested
    @DisplayName("V2 - Redis 검색어 저장 포함")
    class V2Test {

        @BeforeEach
        void setUpRedis() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(redisTemplate.getExpire(anyString())).willReturn(-1L);
        }

        @Test
        @DisplayName("V2 소설 검색 - 로그인 시 Redis 저장과 Redis TTL 00시 설정")
        void searchNovelsV2_loggedIn_saveRedis() {
            Page<NovelSearchResponse> mockPage = new PageImpl<>(List.of(), pageable, 0);
            given(customSearchRepository.searchNovelsByTitle("바다", pageable, true)).willReturn(mockPage);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime midnight = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
            Duration expectedTTL = Duration.between(now, midnight);

            searchService.searchNovels("바다", pageable, loggedInAdultUser);

            verify(zSetOperations).add(eq("search:history:1"), eq("바다"), anyDouble());
            verify(zSetOperations).removeRange(eq("search:history:1"), eq(0L), eq(-11L));
            verify(redisTemplate).expire(
                    eq("search:history:1"),
                    argThat(duration -> {
                        long diff = Math.abs(duration.getSeconds() - expectedTTL.getSeconds());
                        return diff <= 5;
                    })
            );
        }

        @Test
        @DisplayName("V2 소설 검색 - 비로그인 시 개인 히스토리 저장 안됨")
        void searchNovelsV2_anonymous_noRedis() {
            Page<NovelSearchResponse> mockPage = new PageImpl<>(List.of(), pageable, 0);
            given(customSearchRepository.searchNovelsByTitle("바다", pageable, false)).willReturn(mockPage);
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

            searchService.searchNovels("바다", pageable, anonymousUser);

            // 인기 랭킹은 올라가지만 개인 히스토리 저장은 안됨
            verify(zSetOperations, never()).add(eq("search:history:1"), any(), anyDouble());
            verify(redisTemplate, never()).expire(any(), any());
        }

        @Test
        @DisplayName("V2 태그 검색 - 로그인 시 태그 조합이 Redis에 저장됨")
        void searchByTagsV2_loggedIn_saveRedis() {
            List<String> tags = List.of("MUNCHKIN", "DUNGEON");
            given(customSearchRepository.searchNovelsByTags(eq(tags), eq(true))).willReturn(List.of());

            searchService.searchByTags(tags, loggedInAdultUser);

            verify(zSetOperations).add(eq("search:history:1"), eq("MUNCHKIN,DUNGEON"), anyDouble());        }

        @Test
        @DisplayName("V2 작가 검색 - 로그인 시 Redis 저장됨")
        void searchAuthorsV2_loggedIn_saveRedis() {
            IntegratedAuthorSearchResponse mockResult = new IntegratedAuthorSearchResponse(
                    List.of(), List.of()
            );
            given(customSearchRepository.searchByAuthorKeyword(eq("백산"), eq(true))).willReturn(mockResult);

            searchService.searchAuthors("백산", loggedInAdultUser);

            verify(zSetOperations).add(eq("search:history:1"), eq("백산"), anyDouble());
        }

        @Test
        @DisplayName("V2 검색 - 빈 키워드는 Redis 저장 안됨")
        void searchNovelsV2_emptyKeyword_noRedis() {
            Page<NovelSearchResponse> mockPage = new PageImpl<>(List.of(), pageable, 0);
            given(customSearchRepository.searchNovelsByTitle("", pageable, false)).willReturn(mockPage);

            searchService.searchNovels("", pageable, loggedInUser);

            verify(redisTemplate, never()).opsForZSet();
        }

        @Test
        @DisplayName("V2 태그 검색 - 성인 유저일 경우 isAdult=true로 레포지토리 호출")
        void searchByTags_AdultUser_Success() {
            // given
            List<String> tags = List.of("FANTASY");
            // 레포지토리 호출 시 true가 넘어가는지 확인하기 위한 설정
            given(customSearchRepository.searchNovelsByTags(eq(tags), eq(true))).willReturn(List.of());

            // when
            searchService.searchByTags(tags, loggedInAdultUser);

            // then
            // 레포지토리에 'true' 값이 정확히 전달되었는지 검증
            verify(customSearchRepository).searchNovelsByTags(eq(tags), eq(true));
        }

        @Test
        @DisplayName("V2 태그 검색 - 미성년 유저일 경우 isAdult=false로 레포지토리 호출")
        void searchByTags_MinorUser_Success() {
            // given
            List<String> tags = List.of("FANTASY");
            given(customSearchRepository.searchNovelsByTags(eq(tags), eq(false))).willReturn(List.of());

            // when
            searchService.searchByTags(tags, loggedInUser);

            // then
            // 레포지토리에 'false' 값이 정확히 전달되었는지 검증
            verify(customSearchRepository).searchNovelsByTags(eq(tags), eq(false));
        }

        @Test
        @DisplayName("V2 태그 검색 - 비로그인 유저일 경우 isAdult=false로 레포지토리 호출")
        void searchByTags_AnonymousUser_Success() {
            // given
            List<String> tags = List.of("FANTASY");
            given(customSearchRepository.searchNovelsByTags(eq(tags), eq(false))).willReturn(List.of());

            // when
            searchService.searchByTags(tags, anonymousUser);

            // then
            verify(customSearchRepository).searchNovelsByTags(eq(tags), eq(false));
        }

        @Test
        @DisplayName("V2 작가 통합 검색 - 성인 여부에 따른 레포지토리 호출 확인")
        void searchAuthorsV2_Adult_Success() {
            // given
            String keyword = "백산";
            // searchByAuthorKeyword가 이제 isAdult를 받는다고 가정 (수정된 로직 반영)
            given(customSearchRepository.searchByAuthorKeyword(eq(keyword), anyBoolean()))
                    .willReturn(new IntegratedAuthorSearchResponse(List.of(), List.of()));

            // when
            searchService.searchAuthors(keyword, loggedInAdultUser);

            // then
            // 성인 유저이므로 true가 전달되어야 함
            verify(customSearchRepository).searchByAuthorKeyword(keyword, true);
        }

        @Nested
        @DisplayName("성인 필터링 및 장애 실패 테스트")
        class FailureTest {

            @Test
            @DisplayName("인증 만료 유저 검색 - 1년 유효기간이 지난 경우 isAdult=false로 전달됨")
            void search_ExpiredAdultUser_TreatedAsMinor() {
                // given: 2년 전에 인증한 유저 (만료됨)
                User expiredUser = mock(User.class);
                given(expiredUser.isAdultVerificationValid()).willReturn(false);
                UserDetailsImpl expiredUserDetails = mock(UserDetailsImpl.class);
                given(expiredUserDetails.getUser()).willReturn(expiredUser);

                List<String> tags = List.of("ADULT");

                // when
                searchService.searchByTags(tags, expiredUserDetails);

                // then: 유효기간이 지났으므로 레포지토리에는 false가 전달되어야 함
                verify(customSearchRepository).searchNovelsByTags(eq(tags), eq(false));
            }

            @Test
            @DisplayName("Redis 장애 발생 시 - 검색어 저장에 실패해도 검색 결과는 반환되어야 함")
            void search_WhenRedisDown_StillReturnsResults() {
                // given: Redis 호출 시 예외 발생 시뮬레이션
                given(redisTemplate.opsForZSet()).willThrow(new RuntimeException("Redis connection failed"));

                String keyword = "일반소설";
                Page<NovelSearchResponse> mockPage = new PageImpl<>(List.of(), pageable, 0);
                given(customSearchRepository.searchNovelsByTitle(anyString(), any(), anyBoolean()))
                        .willReturn(mockPage);

                // when & then: 예외가 밖으로 터지지 않고 정상적으로 응답을 줘야 함[cite: 1]
                assertThat(searchService.searchNovels(keyword, pageable, loggedInAdultUser))
                        .isNotNull();

                // 로그에 "Redis 장애" 경고가 찍혔는지 확인 가능 (생략)
            }

            @Test
            @DisplayName("잘못된 태그 검색 - 존재하지 않는 태그로 검색 시 빈 리스트 반환")
            void search_InvalidTags_ReturnsEmptyList() {
                // given
                List<String> tags = List.of("존재하지않는태그");
                given(customSearchRepository.searchNovelsByTags(eq(tags), anyBoolean()))
                        .willReturn(List.of(new TagGroupSearchResponse("존재하지않는태그", List.of())));

                // when
                List<TagGroupSearchResponse> result = searchService.searchByTags(tags, loggedInAdultUser);

                // then
                assertThat(result.get(0).novels()).isEmpty();
            }
        }
    }
}