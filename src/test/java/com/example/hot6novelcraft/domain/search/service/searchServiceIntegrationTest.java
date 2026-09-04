package com.example.hot6novelcraft.domain.search.service;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class searchServiceIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("인기 검색어 통합 테스트 : 많이 검색된 순서대로 TOP 랭킹을 반환한다")
    void getTopSearchKeywords_IntegrationTest() {
        // given: 유저들이 검색을 막 힘, 비로그인 유저라고 가정
        UserDetailsImpl dummyUser = null; //

        // "먼치킨" 3번 검색 (3점)
        searchService.searchNovels("먼치킨", PageRequest.of(0, 10), dummyUser);
        searchService.searchNovels("먼치킨", PageRequest.of(0, 10), dummyUser);
        searchService.searchNovels("먼치킨", PageRequest.of(0, 10), dummyUser);

        // "로맨스" 2번 검색 (2점)
        searchService.searchNovels("로맨스", PageRequest.of(0, 10), dummyUser);
        searchService.searchNovels("로맨스", PageRequest.of(0, 10), dummyUser);

        // "회귀" 1번 검색 (1점)
        searchService.searchNovels("회귀", PageRequest.of(0, 10), dummyUser);

        // when: 인기 검색어 랭킹을 조회하면?
        List<String> popularKeywords = searchService.getTopSearchKeywords();

        // then: 점수가 높은 순서대로 1,2,3등이 정확히 나와야 함
        assertThat(popularKeywords).hasSize(3);
        assertThat(popularKeywords.get(0)).isEqualTo("먼치킨");
        assertThat(popularKeywords.get(1)).isEqualTo("로맨스");
        assertThat(popularKeywords.get(2)).isEqualTo("회귀");
    }

    @Test
    @DisplayName("최근 검색어 통합 테스트: 똑같은 단어를 검색하면 중복 저장하지 않고 맨 위로 올라온다")
    void getRecentSearchKeywords_DeduplicationTest() throws InterruptedException {
        // given: 로그인한 유저 생성
        // (UserDetailsImpl을 만드는 더미 코드 필요)
        UserDetailsImpl loginUser = createDummyUser(1L);

        // 시간차를 두고 검색 실행 (Score에 타임스탬프가 다르게 들어가도록)
        searchService.searchNovels("로맨스", PageRequest.of(0, 10), loginUser);
        Thread.sleep(10); // 10밀리초 대기
        searchService.searchNovels("먼치킨", PageRequest.of(0, 10), loginUser);
        Thread.sleep(10);

        // "로맨스"를 한 번 더 검색! (원래 2등이었는데 1등으로 올라와야 함)
        searchService.searchNovels("로맨스", PageRequest.of(0, 10), loginUser);

        // when: 내 최근 검색어 조회
        List<String> recentKeywords = searchService.getRecentSearchKeywords(1L);

        // then: 총 검색어는 2개여야 하고, 방금 다시 검색한 "로맨스"가 0번째(맨 앞)에 있어야 한다!
        assertThat(recentKeywords).hasSize(2);
        assertThat(recentKeywords.get(0)).isEqualTo("로맨스");
        assertThat(recentKeywords.get(1)).isEqualTo("먼치킨");
    }

    /**
     * 테스트용 가짜(Dummy) 유저를 생성하는 헬퍼 메서드
     */
    private UserDetailsImpl createDummyUser(Long userId) {
        // 1. 가짜 User 엔티티 생성
        User dummyUser = User.builder()
                .email("test" + userId + "@test.com")
                .password("password123!")
                .nickname("tester" + userId)
                .role(UserRole.READER)
                .build();

        // 2. JPA 엔티티의 @Id 필드는 보통 자동 생성되므로,
        // 테스트용으로 강제로 ID 값을 주입해 줍니다. (Spring의 ReflectionTestUtils 활용)
        ReflectionTestUtils.setField(dummyUser, "id", userId);

        // 3. UserDetailsImpl 로 감싸서 반환
        return new UserDetailsImpl(dummyUser, null);
    }
}
