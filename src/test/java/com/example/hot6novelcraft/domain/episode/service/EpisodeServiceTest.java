package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.episode.dto.cache.EpisodeContentCache;
import com.example.hot6novelcraft.domain.episode.dto.request.EpisodeCreateRequest;
import com.example.hot6novelcraft.domain.episode.dto.request.EpisodeUpdateRequest;
import com.example.hot6novelcraft.domain.episode.dto.response.*;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorFollowRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class EpisodeServiceTest {

    @Mock
    EpisodeRepository episodeRepository;

    @Mock
    NovelRepository novelRepository;

    @InjectMocks
    EpisodeService episodeService;

    @Mock
    PointHistoryRepository pointHistoryRepository;

    @Mock
    EpisodeCacheService episodeCacheService;

    @Mock
    UserRepository userRepository;

    @Mock
    AuthorFollowRepository authorFollowRepository;

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

    // 회차 Mock
    private Episode 회차(Long novelId, int episodeNumber) {
        Episode episode = mock(Episode.class);
        given(episode.getId()).willReturn(1L);
        given(episode.getNovelId()).willReturn(novelId);
        given(episode.getEpisodeNumber()).willReturn(episodeNumber);
        given(episode.isDeleted()).willReturn(false);
        return episode;
    }

    // 회차 생성 요청 Mock
    private EpisodeCreateRequest 회차생성요청(int episodeNumber) {
        EpisodeCreateRequest request = mock(EpisodeCreateRequest.class);
        given(request.episodeNumber()).willReturn(episodeNumber);
        given(request.title()).willReturn(episodeNumber + "화 제목");
        given(request.content()).willReturn("본문 내용");
        return request;
    }

    // ==================== 회차 생성 ====================

    @Test
    void 회차생성_1화_무료로_성공() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);
        EpisodeCreateRequest request = 회차생성요청(1);
        Episode episode = 회차(1L, 1);

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.existsByNovelIdAndEpisodeNumberAndIsDeletedFalse(1L, 1)).willReturn(false);
        given(episodeRepository.countByNovelIdAndIsDeletedFalse(1L)).willReturn(0);
        given(episodeRepository.save(any())).willReturn(episode);

        EpisodeCreateResponse response = episodeService.createEpisode(1L, request, userDetails);

        assertEquals(episode.getId(), response.episodeId());
    }

    @Test
    void 회차생성_3화_유료로_성공() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);
        EpisodeCreateRequest request = 회차생성요청(3);
        Episode episode = 회차(1L, 3);

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.existsByNovelIdAndEpisodeNumberAndIsDeletedFalse(1L, 3)).willReturn(false);
        given(episodeRepository.countByNovelIdAndIsDeletedFalse(1L)).willReturn(2);
        given(episodeRepository.save(any())).willReturn(episode);

        EpisodeCreateResponse response = episodeService.createEpisode(1L, request, userDetails);

        assertEquals(episode.getId(), response.episodeId());
    }

    @Test
    void 회차생성_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();
        EpisodeCreateRequest request = 회차생성요청(1);

        assertThrows(ServiceErrorException.class,
                () -> episodeService.createEpisode(1L, request, userDetails));
    }

    @Test
    void 회차생성_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가(); // userId = 1L
        Novel novel = 소설(2L); // authorId = 2L
        EpisodeCreateRequest request = 회차생성요청(1);

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> episodeService.createEpisode(1L, request, userDetails));
    }

    @Test
    void 회차생성_회차번호중복이면_실패() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);
        EpisodeCreateRequest request = 회차생성요청(1);

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.existsByNovelIdAndEpisodeNumberAndIsDeletedFalse(1L, 1)).willReturn(true);

        assertThrows(ServiceErrorException.class,
                () -> episodeService.createEpisode(1L, request, userDetails));
    }

    @Test
    void 회차생성_순서안맞으면_실패() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);
        EpisodeCreateRequest request = 회차생성요청(3); // 1화만 있는데 3화 생성 시도

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.existsByNovelIdAndEpisodeNumberAndIsDeletedFalse(1L, 3)).willReturn(false);
        given(episodeRepository.countByNovelIdAndIsDeletedFalse(1L)).willReturn(1); // 1화만 있음

        assertThrows(ServiceErrorException.class,
                () -> episodeService.createEpisode(1L, request, userDetails));
    }

    // ==================== 회차 수정 ====================

    @Test
    void 회차수정_본인소설회차이면_성공() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(1L, 1);
        Novel novel = 소설(1L);

        EpisodeUpdateRequest request = mock(EpisodeUpdateRequest.class);
        given(request.title()).willReturn("수정된 제목");
        given(request.content()).willReturn("수정된 본문");

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        EpisodeUpdateResponse response = episodeService.updateEpisode(1L, request, userDetails);

        assertEquals(episode.getId(), response.episodeId());
    }

    @Test
    void 회차수정_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();
        EpisodeUpdateRequest request = mock(EpisodeUpdateRequest.class);

        assertThrows(ServiceErrorException.class,
                () -> episodeService.updateEpisode(1L, request, userDetails));
    }

    @Test
    void 회차수정_회차없으면_실패() {
        UserDetailsImpl userDetails = 작가();
        EpisodeUpdateRequest request = mock(EpisodeUpdateRequest.class);

        given(episodeRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> episodeService.updateEpisode(1L, request, userDetails));
    }

    @Test
    void 회차수정_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가(); // userId = 1L
        Episode episode = 회차(1L, 1);
        Novel novel = 소설(2L); // authorId = 2L

        EpisodeUpdateRequest request = mock(EpisodeUpdateRequest.class);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> episodeService.updateEpisode(1L, request, userDetails));
    }

    // ==================== 회차 삭제 ====================

    @Test
    void 회차삭제_마지막회차이면_성공() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(1L, 5);
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.existsByNovelIdAndEpisodeNumberGreaterThanAndIsDeletedFalse(1L, 5)).willReturn(false);

        EpisodeDeleteResponse response = episodeService.deleteEpisode(1L, userDetails);

        assertEquals(episode.getId(), response.episodeId());
    }

    @Test
    void 회차삭제_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        assertThrows(ServiceErrorException.class,
                () -> episodeService.deleteEpisode(1L, userDetails));
    }

    @Test
    void 회차삭제_마지막회차아니면_실패() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(1L, 3);
        Novel novel = 소설(1L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.existsByNovelIdAndEpisodeNumberGreaterThanAndIsDeletedFalse(1L, 3)).willReturn(true);

        assertThrows(ServiceErrorException.class,
                () -> episodeService.deleteEpisode(1L, userDetails));
    }

    @Test
    void 회차삭제_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가(); // userId = 1L
        Episode episode = 회차(1L, 5);
        Novel novel = 소설(2L); // authorId = 2L

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> episodeService.deleteEpisode(1L, userDetails));
    }

    // ==================== 회차 발행 ====================

    @Test
    void 회차발행_정상이면_성공() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(1L, 1);
        Novel novel = 소설(1L);
        User author = mock(User.class);
        given(author.getNickname()).willReturn("작가닉네임");

        given(episode.getStatus()).willReturn(EpisodeStatus.DRAFT);
        given(episode.getContent()).willReturn("본문 내용");
        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.existsByNovelIdAndEpisodeNumberLessThanAndStatusNotAndIsDeletedFalse(
                1L, 1, EpisodeStatus.PUBLISHED)).willReturn(false);
        given(userRepository.findById(any())).willReturn(Optional.of(author));
        given(authorFollowRepository.findFollowerIdsByFollowingId(any())).willReturn(List.of());

        EpisodePublishResponse response = episodeService.publishEpisode(1L, userDetails);

        assertEquals(episode.getId(), response.episodeId());
    }

    @Test
    void 회차발행_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        assertThrows(ServiceErrorException.class,
                () -> episodeService.publishEpisode(1L, userDetails));
    }

    @Test
    void 회차발행_이미발행된회차이면_실패() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(1L, 1);
        Novel novel = 소설(1L);

        given(episode.getStatus()).willReturn(EpisodeStatus.PUBLISHED);
        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> episodeService.publishEpisode(1L, userDetails));
    }

    @Test
    void 회차발행_본문없으면_실패() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(1L, 1);
        Novel novel = 소설(1L);

        given(episode.getStatus()).willReturn(EpisodeStatus.DRAFT);
        given(episode.getContent()).willReturn(null);
        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> episodeService.publishEpisode(1L, userDetails));
    }

    @Test
    void 회차발행_이전회차미발행이면_실패() {
        UserDetailsImpl userDetails = 작가();
        Episode episode = 회차(1L, 3); // 3화
        Novel novel = 소설(1L);

        given(episode.getStatus()).willReturn(EpisodeStatus.DRAFT);
        given(episode.getContent()).willReturn("본문 내용");
        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.existsByNovelIdAndEpisodeNumberLessThanAndStatusNotAndIsDeletedFalse(
                1L, 3, EpisodeStatus.PUBLISHED)).willReturn(true); // 이전 회차 미발행

        assertThrows(ServiceErrorException.class,
                () -> episodeService.publishEpisode(1L, userDetails));
    }

    @Test
    void 회차발행_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가(); // userId = 1L
        Episode episode = 회차(1L, 1);
        Novel novel = 소설(2L); // authorId = 2L

        given(episode.getStatus()).willReturn(EpisodeStatus.DRAFT);
        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> episodeService.publishEpisode(1L, userDetails));
    }

    // ==================== 회차 목록 조회 ====================

    @Test
    void 회차목록조회_성공() {
        Novel novel = 소설(1L);
        Page<EpisodeListResponse> episodePage = new PageImpl<>(List.of(
                new EpisodeListResponse(1L, 1, "1화 제목", true, 0, 150L, null),
                new EpisodeListResponse(2L, 2, "2화 제목", true, 0, 120L, null)
        ));

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.findEpisodeListByNovelId(eq(1L), any())).willReturn(episodePage);

        PageResponse<EpisodeListResponse> response = episodeService.getEpisodeList(1L, Pageable.ofSize(20));

        assertNotNull(response);
        assertEquals(2, response.content().size());
    }

    @Test
    void 회차목록조회_소설없으면_실패() {
        given(novelRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> episodeService.getEpisodeList(1L, Pageable.ofSize(20)));
    }

    // ==================== 회차 본문 조회 V1 ====================

    @Test
    void 회차본문조회V1_무료회차_성공() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(1L, 1);
        Novel novel = 소설(1L);
        given(episode.getStatus()).willReturn(EpisodeStatus.PUBLISHED);
        given(episode.isFree()).willReturn(true);
        given(episode.getTitle()).willReturn("1화 제목");
        given(episode.getContent()).willReturn("1화 본문");
        given(episode.getLikeCount()).willReturn(100L);
        given(episode.getPointPrice()).willReturn(0);
        given(novel.getTags()).willReturn(null);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeCacheService.isFirstView(2L, 1L)).willReturn(false);

        EpisodeDetailResponse response = episodeService.getEpisodeContentV1(1L, userDetails);

        assertNotNull(response);
        assertEquals(episode.getId(), response.episodeId());
    }


    @Test
    void 회차본문조회V1_유료회차_구매이력있으면_성공() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(1L, 3);
        Novel novel = 소설(1L);
        given(episode.getStatus()).willReturn(EpisodeStatus.PUBLISHED);
        given(episode.isFree()).willReturn(false);
        given(novel.getTags()).willReturn(null);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(pointHistoryRepository.existsByUserIdAndEpisodeIdAndType(
                2L, 1L, PointHistoryType.NOVEL)).willReturn(true);
        given(episodeCacheService.isFirstView(2L, 1L)).willReturn(false);

        EpisodeDetailResponse response = episodeService.getEpisodeContentV1(1L, userDetails);

        assertNotNull(response);
    }

    @Test
    void 회차본문조회V1_유료회차_구매이력없으면_실패() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(1L, 3);
        given(episode.getStatus()).willReturn(EpisodeStatus.PUBLISHED);
        given(episode.isFree()).willReturn(false);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(pointHistoryRepository.existsByUserIdAndEpisodeIdAndType(
                2L, 1L, PointHistoryType.NOVEL)).willReturn(false);

        assertThrows(ServiceErrorException.class,
                () -> episodeService.getEpisodeContentV1(1L, userDetails));
    }

    @Test
    void 회차본문조회V1_발행안된회차이면_실패() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(1L, 1);
        given(episode.getStatus()).willReturn(EpisodeStatus.DRAFT);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));

        assertThrows(ServiceErrorException.class,
                () -> episodeService.getEpisodeContentV1(1L, userDetails));
    }

    @Test
    void 회차본문조회V1_회차없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        given(episodeRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> episodeService.getEpisodeContentV1(1L, userDetails));
    }

    @Test
    void 회차본문조회V1_첫조회시_조회수증가() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(1L, 1);
        Novel novel = 소설(1L);
        given(episode.getStatus()).willReturn(EpisodeStatus.PUBLISHED);
        given(episode.isFree()).willReturn(true);
        given(novel.getTags()).willReturn(null);
        given(novel.getStatus()).willReturn(com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus.ONGOING);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeCacheService.isFirstView(2L, 1L)).willReturn(true);
        given(episodeCacheService.isFirstEpisodeView(2L, 1L)).willReturn(true);

        EpisodeDetailResponse response = episodeService.getEpisodeContentV1(1L, userDetails);

        assertNotNull(response);
        verify(episodeCacheService).increaseViewCount(1L);
        verify(episodeCacheService).increaseEpisodeDailyViewCount(1L);
    }

    // ==================== 회차 본문 조회 V2 ====================

    @Test
    void 회차본문조회V2_캐시HIT이면_캐시반환_성공() {
        UserDetailsImpl userDetails = 독자();
        Novel novel = 소설(1L);
        given(novel.getTags()).willReturn(null);

        EpisodeContentCache cached = new EpisodeContentCache(
                1L, 1L, 1, "1화", "캐시된 본문", 100L, true, 0
        );

        given(episodeCacheService.getContentCache(1L)).willReturn(cached);
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeCacheService.isFirstView(2L, 1L)).willReturn(false);

        EpisodeDetailResponse response = episodeService.getEpisodeContentV2(1L, userDetails);

        assertNotNull(response);
        assertEquals("캐시된 본문", response.content());
    }

    @Test
    void 회차본문조회V2_캐시MISS_비인기작이면_DB조회만() {
        UserDetailsImpl userDetails = 독자();
        Novel novel = 소설(1L);
        given(novel.getTags()).willReturn(null);
        given(novel.getStatus()).willReturn(com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus.ONGOING);

        EpisodeContentCache content = new EpisodeContentCache(
                1L, 1L, 1, "1화", "DB 본문", 100L, true, 0
        );

        given(episodeCacheService.getContentCache(1L)).willReturn(null);
        given(episodeRepository.findContentCacheById(1L)).willReturn(content);
        given(episodeCacheService.increaseHotKeyCount(1L)).willReturn(10L);
        given(episodeCacheService.isHotNovel(10L)).willReturn(false);
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeCacheService.isFirstView(2L, 1L)).willReturn(false);

        EpisodeDetailResponse response = episodeService.getEpisodeContentV2(1L, userDetails);

        assertNotNull(response);
        verify(episodeCacheService, org.mockito.Mockito.never()).saveContentCache(any(), any());
    }

    @Test
    void 회차본문조회V2_캐시MISS_인기작이면_DB조회후_캐싱() {
        UserDetailsImpl userDetails = 독자();
        Novel novel = 소설(1L);
        given(novel.getTags()).willReturn(null);
        given(novel.getStatus()).willReturn(com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus.ONGOING);

        EpisodeContentCache content = new EpisodeContentCache(
                1L, 1L, 1, "1화", "DB 본문", 100L, true, 0
        );

        given(episodeCacheService.getContentCache(1L)).willReturn(null);
        given(episodeRepository.findContentCacheById(1L)).willReturn(content);
        given(episodeCacheService.increaseHotKeyCount(1L)).willReturn(100L);
        given(episodeCacheService.isHotNovel(100L)).willReturn(true);
        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeCacheService.isFirstView(2L, 1L)).willReturn(false);

        EpisodeDetailResponse response = episodeService.getEpisodeContentV2(1L, userDetails);

        assertNotNull(response);
        verify(episodeCacheService).saveContentCache(1L, content);
    }

    @Test
    void 회차본문조회V2_회차없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        given(episodeCacheService.getContentCache(1L)).willReturn(null);
        given(episodeRepository.findContentCacheById(1L)).willReturn(null);

        assertThrows(ServiceErrorException.class,
                () -> episodeService.getEpisodeContentV2(1L, userDetails));
    }

    // ==================== 작가용 회차 목록 조회 ====================

    @Test
    void 작가회차목록조회_성공() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);

        Page<AuthorEpisodeListResponse> episodePage = new PageImpl<>(List.of(
                new AuthorEpisodeListResponse(
                        1L, 1, "1화", EpisodeStatus.PUBLISHED,
                        true, 0, null, null
                ),
                new AuthorEpisodeListResponse(
                        2L, 2, "2화 미발행", EpisodeStatus.DRAFT,
                        false, 200, null, null
                )
        ));

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.findAuthorEpisodeList(eq(1L), any())).willReturn(episodePage);

        PageResponse<AuthorEpisodeListResponse> response =
                episodeService.getAuthorEpisodeList(1L, userDetails, Pageable.ofSize(20));

        assertNotNull(response);
        assertEquals(2, response.content().size());
    }

    @Test
    void 작가회차목록조회_작가권한없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        assertThrows(ServiceErrorException.class,
                () -> episodeService.getAuthorEpisodeList(1L, userDetails, Pageable.ofSize(20)));
    }

    @Test
    void 작가회차목록조회_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가(); // userId = 1L
        Novel novel = 소설(2L); // authorId = 2L (다른 작가)

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> episodeService.getAuthorEpisodeList(1L, userDetails, Pageable.ofSize(20)));
    }
}