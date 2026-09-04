package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeLikeResponse;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.EpisodeLike;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeLikeRepository;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class EpisodeLikeServiceTest {

    @Mock
    EpisodeLikeRepository episodeLikeRepository;

    @Mock
    EpisodeRepository episodeRepository;

    @InjectMocks
    EpisodeLikeService episodeLikeService;

    // 독자 Mock (userId = 1L)
    private UserDetailsImpl 독자() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getRole()).willReturn(UserRole.READER);

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(user);
        return userDetails;
    }

    // 회차 Mock
    private Episode 회차(EpisodeStatus status, long likeCount) {
        Episode episode = mock(Episode.class);
        given(episode.getStatus()).willReturn(status);
        given(episode.isDeleted()).willReturn(false);
        given(episode.getLikeCount()).willReturn(likeCount);
        return episode;
    }

    // ==================== 좋아요 ====================

    @Test
    void 좋아요_처음이면_생성_성공() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(EpisodeStatus.PUBLISHED, 1L);

        // findById가 두 번 호출됨 (처음 + 최신 카운트 조회)
        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(episodeLikeRepository.findByUserIdAndEpisodeId(1L, 1L))
                .willReturn(Optional.empty());

        EpisodeLikeResponse response = episodeLikeService.toggleLike(1L, userDetails);

        assertTrue(response.isLiked());
        verify(episodeRepository).incrementLikeCount(1L);
    }

    @Test
    void 좋아요_이미있으면_취소_성공() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(EpisodeStatus.PUBLISHED, 0L);
        EpisodeLike existing = mock(EpisodeLike.class);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(episodeLikeRepository.findByUserIdAndEpisodeId(1L, 1L))
                .willReturn(Optional.of(existing));

        EpisodeLikeResponse response = episodeLikeService.toggleLike(1L, userDetails);

        assertFalse(response.isLiked());
        verify(episodeRepository).decrementLikeCount(1L);
    }

    @Test
    void 좋아요_회차없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        given(episodeRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> episodeLikeService.toggleLike(1L, userDetails));
    }

    @Test
    void 좋아요_삭제된회차이면_실패() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = mock(Episode.class);
        given(episode.isDeleted()).willReturn(true);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));

        assertThrows(ServiceErrorException.class,
                () -> episodeLikeService.toggleLike(1L, userDetails));
    }

    @Test
    void 좋아요_발행안된회차이면_실패() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(EpisodeStatus.DRAFT, 0L);

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));

        assertThrows(ServiceErrorException.class,
                () -> episodeLikeService.toggleLike(1L, userDetails));
    }
}