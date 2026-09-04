package com.example.hot6novelcraft.domain.novel.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.novel.dto.request.NovelWikiCreateRequest;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelWikiCreateResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelWikiDeleteResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelWikiResponse;
import com.example.hot6novelcraft.domain.novel.entity.NovelWiki;
import com.example.hot6novelcraft.domain.novel.entity.enums.WikiCategory;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelWikiRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class NovelWikiServiceTest {

    @Mock
    NovelWikiRepository novelWikiRepository;

    @Mock
    NovelRepository novelRepository;

    @InjectMocks
    NovelWikiService novelWikiService;

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    // 작가 Mock
    private UserDetailsImpl 작가() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getRole()).willReturn(UserRole.AUTHOR);

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(user);
        return userDetails;
    }

    // 독자 Mock
    private UserDetailsImpl 독자() {
        User user = mock(User.class);
        given(user.getId()).willReturn(2L);
        given(user.getRole()).willReturn(UserRole.READER);

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(user);
        return userDetails;
    }

    // 소설 Mock
    private Novel 소설(Long authorId) {
        Novel novel = mock(Novel.class);
        given(novel.getId()).willReturn(1L);
        given(novel.getAuthorId()).willReturn(authorId);
        given(novel.isDeleted()).willReturn(false);
        return novel;
    }

    // 설정집 Mock
    private NovelWiki 설정집() {
        NovelWiki wiki = mock(NovelWiki.class);
        given(wiki.getId()).willReturn(1L);
        given(wiki.getNovelId()).willReturn(1L);
        return wiki;
    }

    // 설정집 생성 요청 Mock
    private NovelWikiCreateRequest 설정집생성요청() {
        NovelWikiCreateRequest request = mock(NovelWikiCreateRequest.class);
        given(request.category()).willReturn(WikiCategory.CHARACTER);
        given(request.title()).willReturn("이준혁");
        given(request.content()).willReturn("주인공");
        return request;
    }

    // ==================== 설정집 저장 ====================

    @Test
    void 설정집저장_성공() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);
        NovelWiki wiki = 설정집();
        NovelWikiCreateRequest request = 설정집생성요청();

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(novelWikiRepository.save(any())).willReturn(wiki);

        NovelWikiCreateResponse response = novelWikiService.createWiki(1L, request, userDetails);

        assertEquals(wiki.getId(), response.wikiId());
    }

    @Test
    void 설정집저장_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();
        NovelWikiCreateRequest request = 설정집생성요청();

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.createWiki(1L, request, userDetails));
    }

    @Test
    void 설정집저장_소설없으면_실패() {
        UserDetailsImpl userDetails = 작가();
        NovelWikiCreateRequest request = 설정집생성요청();

        given(novelRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.createWiki(1L, request, userDetails));
    }

    @Test
    void 설정집저장_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가(); // userId = 1L
        Novel novel = 소설(2L); // authorId = 2L
        NovelWikiCreateRequest request = 설정집생성요청();

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.createWiki(1L, request, userDetails));
    }

    // ==================== 설정집 삭제 ====================

    @Test
    void 설정집삭제_성공() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);
        NovelWiki wiki = 설정집();

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(novelWikiRepository.findById(1L)).willReturn(Optional.of(wiki));

        NovelWikiDeleteResponse response = novelWikiService.deleteWiki(1L, 1L, userDetails);

        assertEquals(wiki.getId(), response.wikiId());
    }

    @Test
    void 설정집삭제_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.deleteWiki(1L, 1L, userDetails));
    }

    @Test
    void 설정집삭제_소설없으면_실패() {
        UserDetailsImpl userDetails = 작가();

        given(novelRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.deleteWiki(1L, 1L, userDetails));
    }

    @Test
    void 설정집삭제_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가(); // userId = 1L
        Novel novel = 소설(2L); // authorId = 2L

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.deleteWiki(1L, 1L, userDetails));
    }

    @Test
    void 설정집삭제_설정집없으면_실패() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(novelWikiRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.deleteWiki(1L, 1L, userDetails));
    }

    // ==================== 설정집 조회 ====================

    @Test
    void 설정집조회_캐시없으면_DB조회후_캐싱_성공() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);
        NovelWiki wiki = 설정집();

        // RedisTemplate Mock 설정
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(any())).willReturn(null); // 캐시 미스

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(novelWikiRepository.findAllByNovelId(1L)).willReturn(List.of(wiki));

        List<NovelWikiResponse> response = novelWikiService.getWikiList(1L, userDetails);

        assertNotNull(response);
        assertEquals(1, response.size());
    }

    @Test
    void 설정집조회_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.getWikiList(1L, userDetails));
    }

    @Test
    void 설정집조회_소설없으면_실패() {
        UserDetailsImpl userDetails = 작가();

        given(novelRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.getWikiList(1L, userDetails));
    }

    @Test
    void 설정집조회_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가(); // userId = 1L
        Novel novel = 소설(2L); // authorId = 2L

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> novelWikiService.getWikiList(1L, userDetails));
    }
}