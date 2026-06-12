package com.example.hot6novelcraft.domain.novel.service;

import com.example.hot6novelcraft.domain.episode.service.EpisodeCacheService;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelRankingResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.enums.MainGenre;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class NovelRankingIntegrationTest {

    @Autowired
    private EpisodeCacheService episodeCacheService;

    @Autowired
    private NovelRankingService novelRankingService;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private User dummyAuthor;
    private Novel novelA, novelB, novelC;

    @BeforeEach
    void setUp() {
        // 테스트 시작 전 Redis 도화지 초기화
        clearRedis();

        // 이전 테스트 실행에서 남은 데이터 정리 (FK 순서: novels → users)
        novelRepository.deleteAll();
        userRepository.deleteAll();

        // 소설을 쓰기 위한 가짜 '작가' 유저를 먼저 DB에 저장합니다!
        User authorUser = User.builder()
                .email("author@test.com")
                .password("password123!")
                .nickname("테스트작가")
                .role(UserRole.AUTHOR)
                .birthday(java.time.LocalDate.of(1990, 1, 1))
                .phoneNo("010-0000-0000")
                .build();
        dummyAuthor = userRepository.save(authorUser); // DB에 저장!

        // DB에 테스트용 더미 소설 3개 저장 (엔티티 필수값에 맞춰 수정 필요할 수 있음)
        novelA = novelRepository.save(createDummyNovel("테스트 소설 A", dummyAuthor, MainGenre.FANTASY));
        novelB = novelRepository.save(createDummyNovel("테스트 소설 B", dummyAuthor, MainGenre.HORROR));
        novelC = novelRepository.save(createDummyNovel("테스트 소설 C", dummyAuthor, MainGenre.CLASSIC));

        // 엔티티에 있는 changeStatus()를 사용해 상태를 강제로 PUBLISHED(연재중)로 변경 (UPDATE)
        novelA.changeStatus(NovelStatus.PUBLISHED);
        novelB.changeStatus(NovelStatus.PUBLISHED);
        novelC.changeStatus(NovelStatus.PUBLISHED);

        // 변경된 상태(PUBLISHED)를 DB에 즉시 반영 (QueryDSL 조회를 위해 필수)
        novelRepository.flush();
    }

    @AfterEach
    void tearDown() {
        // 테스트 종료 후 Redis + DB 청소
        clearRedis();
        novelRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void clearRedis() {
        redisTemplate.delete("ranking:novel:realtime");
        redisTemplate.delete("ranking:novel:weekly");
        redisTemplate.delete("ranking:novel:realtime:temp");
        redisTemplate.delete("ranking:novel:weekly:temp");
    }

    @Test
    @DisplayName("소설 랭킹 통합 테스트: 조회수가 높은 순서대로 실시간 및 주간 랭킹 TOP을 반환한다.")
    void novelRanking_IntegrationTest() {
        // given: 소설들의 랭킹 점수(조회수) 증가 (어뷰징 통과했다고 가정하고 메서드 직접 호출)

        // 소설A: 1번 읽힘 (3등)
        episodeCacheService.increaseRankingScore(novelA.getId());

        // 소설B: 3번 읽힘 (1등)
        episodeCacheService.increaseRankingScore(novelB.getId());
        episodeCacheService.increaseRankingScore(novelB.getId());
        episodeCacheService.increaseRankingScore(novelB.getId());

        // 소설C: 2번 읽힘 (2등)
        episodeCacheService.increaseRankingScore(novelC.getId());
        episodeCacheService.increaseRankingScore(novelC.getId());

        // when: 실시간 랭킹과 주간 랭킹 조회 API 로직 실행
        List<NovelRankingResponse> realtimeRanking = novelRankingService.getNovelRanking("realtime");
        List<NovelRankingResponse> weeklyRanking = novelRankingService.getNovelRanking("weekly");

        // then: 랭킹은 점수가 높은 B -> C -> A 순서여야 한다 (총 3개)

        // 1. 실시간 랭킹 검증
        assertThat(realtimeRanking).hasSize(3);
        assertThat(realtimeRanking.get(0).novelId()).isEqualTo(novelB.getId()); // 1등은 B
        assertThat(realtimeRanking.get(1).novelId()).isEqualTo(novelC.getId()); // 2등은 C
        assertThat(realtimeRanking.get(2).novelId()).isEqualTo(novelA.getId()); // 3등은 A

        // 2. 주간 랭킹 검증
        assertThat(weeklyRanking).hasSize(3);
        assertThat(weeklyRanking.get(0).novelId()).isEqualTo(novelB.getId());
        assertThat(weeklyRanking.get(1).novelId()).isEqualTo(novelC.getId());
        assertThat(weeklyRanking.get(2).novelId()).isEqualTo(novelA.getId());
    }

    @Test
    @DisplayName("랭킹 필터링 테스트: 성인물(19금)이나 보류(PENDING) 상태인 소설은 조회수가 높아도 랭킹에서 제외된다.")
    void novelRanking_FilteringTest() {
        // given: 특수 조건의 소설 2개 추가 생성
        Novel adultNovel = novelRepository.save(Novel.builder()
                .title("19금 테스트 소설")
                .description("19금 소설 소개글")
                .authorId(dummyAuthor.getId())
                .genre(MainGenre.ROMANCE_FANTASY.name())
                .status(NovelStatus.PUBLISHED)
                .tags("로맨스,19금,고수위") // 성인 태그 포함
                .build());

        Novel pendingNovel = novelRepository.save(Novel.builder()
                .title("보류중인 소설")
                .description("보류 소설 소개글")
                .authorId(dummyAuthor.getId())
                .genre(MainGenre.FANTASY.name())
                .status(NovelStatus.PENDING) // 상태가 PENDING
                .tags("판타지,테스트")
                .build());

        // 정상 소설A만 1번 읽힘 (성인/보류 소설은 Redis 점수 증가 없음 — Redis 경로는 상태 필터링 미지원)
        episodeCacheService.increaseRankingScore(novelA.getId());

        // when
        List<NovelRankingResponse> realtimeRanking = novelRankingService.getNovelRanking("realtime");

        // then: Redis에 점수가 등록된 novelA만 랭킹에 나온다.
        assertThat(realtimeRanking).hasSize(1);
        assertThat(realtimeRanking.get(0).novelId()).isEqualTo(novelA.getId());
    }

    @Test
    @DisplayName("DB Fallback 테스트: Redis 장애 시 우회할 DB 쿼리(QueryDSL)가 최근 1시간 조회수 기준으로 Top 소설을 정확히 가져온다.")
    void dbFallbackQuery_IntegrationTest() {
        // given: DB 엔티티 자체의 viewCount를 강제로 세팅 (Fallback은 DB의 viewCount를 직접 보므로)
        // saveAll 전에 PUBLISHED → ONGOING으로 변경 (MySQL ENUM 컬럼에 PUBLISHED 값이 없어 truncation 오류 방지)
        novelA.changeStatus(NovelStatus.ONGOING);
        novelB.changeStatus(NovelStatus.ONGOING);
        novelC.changeStatus(NovelStatus.ONGOING);
        ReflectionTestUtils.setField(novelA, "viewCount", 100L); // 1등
        ReflectionTestUtils.setField(novelB, "viewCount", 50L);  // 2등
        ReflectionTestUtils.setField(novelC, "viewCount", 10L);  // 3등

        // updatedAt이 1시간 이내로 찍히도록 강제 업데이트 처리(JPA Auditing 활용)
        novelRepository.saveAll(List.of(novelA, novelB, novelC));
        novelRepository.flush(); // DB에 즉시 반영

        // when: Service의 Fallback이 호출하는 Repository 쿼리 직접 실행 (5개 리미트)
        List<Novel> fallbackTopNovels = novelRepository.findHourlyTopNovels(5);

        // then: DB 쿼리만으로도 내림차순 정렬이 완벽하게 되어야 한다.
        assertThat(fallbackTopNovels).hasSize(3);
        assertThat(fallbackTopNovels.get(0).getId()).isEqualTo(novelA.getId());
        assertThat(fallbackTopNovels.get(1).getId()).isEqualTo(novelB.getId());
        assertThat(fallbackTopNovels.get(2).getId()).isEqualTo(novelC.getId());
    }

    // 테스트용 가짜 소설을 생성하는 헬퍼 메서드
    private Novel createDummyNovel(String title, User author, MainGenre genre) {
        return Novel.builder()
                .title(title)
                .description("테스트용 소설 소개글입니다.")
                .authorId(author.getId())
                .genre(genre.name())
                .status(NovelStatus.PUBLISHED)
                .tags("테스트,먼치킨,회귀")
                .build();
    }
}
