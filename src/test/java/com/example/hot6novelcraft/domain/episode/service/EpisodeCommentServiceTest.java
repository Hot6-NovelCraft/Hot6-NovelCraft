package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.episode.dto.request.EpisodeCommentCreateRequest;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeCommentCreateResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeCommentListResponse;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.EpisodeComment;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeCommentRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class EpisodeCommentServiceTest {

    @Mock
    EpisodeCommentRepository episodeCommentRepository;

    @Mock
    EpisodeRepository episodeRepository;

    @Mock
    EpisodeCommentCacheService episodeCommentCacheService ;

    @InjectMocks
    EpisodeCommentService episodeCommentService;

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
    private Episode 회차(EpisodeStatus status) {
        Episode episode = mock(Episode.class);
        given(episode.getId()).willReturn(1L);
        given(episode.getStatus()).willReturn(status);
        given(episode.isDeleted()).willReturn(false);
        return episode;
    }

    // 댓글 Mock
    private EpisodeComment 댓글(Long userId) {
        EpisodeComment comment = mock(EpisodeComment.class);
        given(comment.getId()).willReturn(1L);
        given(comment.getUserId()).willReturn(userId);
        given(comment.getEpisodeId()).willReturn(1L);
        return comment;
    }

    // 댓글 작성 요청 Mock
    private EpisodeCommentCreateRequest 댓글작성요청() {
        EpisodeCommentCreateRequest request = mock(EpisodeCommentCreateRequest.class);
        given(request.content()).willReturn("재밌어요!");
        return request;
    }

    // ==================== 댓글 작성 ====================

    @Test
    void 댓글작성_발행된회차이면_성공() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(EpisodeStatus.PUBLISHED);
        EpisodeComment comment = 댓글(1L);
        EpisodeCommentCreateRequest request = 댓글작성요청();

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));
        given(episodeCommentRepository.save(any())).willReturn(comment);

        EpisodeCommentCreateResponse response =
                episodeCommentService.createComment(1L, request, userDetails);

        assertNotNull(response);
        assertEquals(1L, response.commentId());
        verify(episodeCommentCacheService).evictCommentCache(1L);
    }

    @Test
    void 댓글작성_회차없으면_실패() {
        UserDetailsImpl userDetails = 독자();
        EpisodeCommentCreateRequest request = 댓글작성요청();

        given(episodeRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> episodeCommentService.createComment(1L, request, userDetails));
    }

    @Test
    void 댓글작성_발행안된회차이면_실패() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = 회차(EpisodeStatus.DRAFT);
        EpisodeCommentCreateRequest request = 댓글작성요청();

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));

        assertThrows(ServiceErrorException.class,
                () -> episodeCommentService.createComment(1L, request, userDetails));
    }

    @Test
    void 댓글작성_삭제된회차이면_실패() {
        UserDetailsImpl userDetails = 독자();
        Episode episode = mock(Episode.class);
        given(episode.isDeleted()).willReturn(true);
        EpisodeCommentCreateRequest request = 댓글작성요청();

        given(episodeRepository.findById(1L)).willReturn(Optional.of(episode));

        assertThrows(ServiceErrorException.class,
                () -> episodeCommentService.createComment(1L, request, userDetails));
    }

    // ==================== 댓글 삭제 ====================

    @Test
    void 댓글삭제_본인댓글이면_성공() {
        UserDetailsImpl userDetails = 독자(); // userId = 1L
        EpisodeComment comment = 댓글(1L);   // 본인 댓글

        given(episodeCommentRepository.findById(1L)).willReturn(Optional.of(comment));

        episodeCommentService.deleteComment(1L, userDetails);

        verify(episodeCommentRepository).delete(comment);
        verify(episodeCommentCacheService).evictCommentCache(1L);
    }

    @Test
    void 댓글삭제_댓글없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        given(episodeCommentRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> episodeCommentService.deleteComment(1L, userDetails));
    }

    @Test
    void 댓글삭제_본인댓글아니면_실패() {
        UserDetailsImpl userDetails = 독자(); // userId = 1L
        EpisodeComment comment = 댓글(2L);   // 다른 유저 댓글

        given(episodeCommentRepository.findById(1L)).willReturn(Optional.of(comment));

        assertThrows(ServiceErrorException.class,
                () -> episodeCommentService.deleteComment(1L, userDetails));
    }

    // ==================== 댓글 목록 조회 ====================

    @Test
    void 댓글목록조회_캐시HIT이면_캐시반환() {
        PageResponse<EpisodeCommentListResponse> cached = new PageResponse<>(
                List.of(), 0, 0, 0L, 20, true
        );

        given(episodeCommentCacheService.getCommentCache(1L, 0)).willReturn(cached);

        PageResponse<EpisodeCommentListResponse> response =
                episodeCommentService.getCommentList(1L, Pageable.ofSize(20));

        assertNotNull(response);
        // DB 조회 없이 캐시에서 반환
        verify(episodeCommentRepository, org.mockito.Mockito.never())
                .findCommentList(any(), any());
    }

    @Test
    void 댓글목록조회_캐시MISS이면_DB조회후_캐싱() {
        Page<EpisodeCommentListResponse> commentPage = new PageImpl<>(List.of(
                new EpisodeCommentListResponse(1L, 100L, "테스트작가", "재밌어요!", null)
        ));

        given(episodeCommentCacheService.getCommentCache(1L, 0)).willReturn(null);
        given(episodeCommentRepository.findCommentList(eq(1L), any()))
                .willReturn(commentPage);

        PageResponse<EpisodeCommentListResponse> response =
                episodeCommentService.getCommentList(1L, Pageable.ofSize(20));

        assertNotNull(response);
        assertEquals(1, response.content().size());
        verify(episodeCommentCacheService).saveCommentCache(eq(1L), eq(0), any());
    }
}