package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeStatResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeStatWithViewResponse;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class EpisodeStatServiceTest {

    @Mock
    EpisodeRepository episodeRepository;

    @Mock
    NovelRepository novelRepository;

    @Mock
    EpisodeCacheService episodeCacheService;

    @InjectMocks
    EpisodeStatService episodeStatService;

    // 작가 Mock
    private UserDetailsImpl 작가() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getRole()).willReturn(UserRole.AUTHOR);
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

    // 회차 통계 Mock
    private EpisodeStatResponse 회차통계(Long episodeId, int episodeNumber) {
        return new EpisodeStatResponse(
                episodeId,
                episodeNumber,
                episodeNumber + "화 제목",
                100L, // likeCount
                10L,  // commentCount
                5L    // purchaseCount
        );
    }

    // ==================== 회차 통계 조회 ====================

    @Test
    void 회차통계조회_성공() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);

        List<EpisodeStatResponse> stats = List.of(
                회차통계(1L, 1),
                회차통계(2L, 2)
        );

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.findEpisodeStatsByNovelId(1L)).willReturn(stats);
        given(episodeCacheService.getEpisodeDailyViewCount(1L)).willReturn(10L);
        given(episodeCacheService.getEpisodeDailyViewCount(2L)).willReturn(5L);

        List<EpisodeStatWithViewResponse> result = episodeStatService.getEpisodeStats(1L, userDetails);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).todayViewCount());
        assertEquals(5L, result.get(1).todayViewCount());
    }

    @Test
    void 회차통계조회_소설없으면_실패() {
        UserDetailsImpl userDetails = 작가();

        given(novelRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> episodeStatService.getEpisodeStats(1L, userDetails));
    }

    @Test
    void 회차통계조회_본인소설아니면_실패() {
        UserDetailsImpl userDetails = 작가(); // userId = 1L
        Novel novel = 소설(2L); // authorId = 2L

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> episodeStatService.getEpisodeStats(1L, userDetails));
    }

    @Test
    void 회차통계조회_삭제된소설이면_실패() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);
        given(novel.isDeleted()).willReturn(true);

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> episodeStatService.getEpisodeStats(1L, userDetails));
    }

    @Test
    void 회차통계조회_회차없으면_빈리스트반환() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.findEpisodeStatsByNovelId(1L)).willReturn(List.of());

        List<EpisodeStatWithViewResponse> result = episodeStatService.getEpisodeStats(1L, userDetails);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void 회차통계조회_오늘조회수없으면_0반환() {
        UserDetailsImpl userDetails = 작가();
        Novel novel = 소설(1L);

        List<EpisodeStatResponse> stats = List.of(회차통계(1L, 1));

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(episodeRepository.findEpisodeStatsByNovelId(1L)).willReturn(stats);
        given(episodeCacheService.getEpisodeDailyViewCount(1L)).willReturn(0L); // 오늘 조회 없음

        List<EpisodeStatWithViewResponse> result = episodeStatService.getEpisodeStats(1L, userDetails);

        assertEquals(0L, result.get(0).todayViewCount());
    }
}