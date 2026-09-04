package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EpisodeExceptionEnum;
import com.example.hot6novelcraft.domain.episode.dto.request.EpisodeCommentCreateRequest;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeCommentCreateResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeCommentListResponse;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.EpisodeComment;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeCommentRepository;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodeCommentService {

    private final EpisodeCommentRepository episodeCommentRepository;
    private final EpisodeRepository episodeRepository;
    private final EpisodeCommentCacheService episodeCommentCacheService;

    // 댓글 작성
    @Transactional
    public EpisodeCommentCreateResponse createComment(
            Long episodeId,
            EpisodeCommentCreateRequest request,
            UserDetailsImpl userDetails
    ) {
        Long userId = userDetails.getUser().getId();

        // 회차 조회
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));

        // 삭제된 회차 체크
        if (episode.isDeleted()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_DELETED);
        }

        // 발행된 회차만 댓글 가능
        if (episode.getStatus() != EpisodeStatus.PUBLISHED) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_PUBLISHED);
        }

        // 댓글 생성
        EpisodeComment comment = EpisodeComment.builder()
                .userId(userId)
                .episodeId(episodeId)
                .content(request.content())
                .build();

        EpisodeComment saved = episodeCommentRepository.save(comment);

        // 댓글 캐시 무효화
        episodeCommentCacheService.evictCommentCache(comment.getEpisodeId());

        return EpisodeCommentCreateResponse.from(saved.getId());
    }

    // 댓글 삭제 (하드 딜리트)
    @Transactional
    public void deleteComment(Long commentId, UserDetailsImpl userDetails) {

        Long userId = userDetails.getUser().getId();

        // 댓글 존재 여부 확인
        EpisodeComment comment = episodeCommentRepository.findById(commentId)
                .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.COMMENT_NOT_FOUND));

        // 본인 댓글인지 확인
        if (!comment.getUserId().equals(userId)) {
            throw new ServiceErrorException(EpisodeExceptionEnum.COMMENT_FORBIDDEN);
        }

        // 댓글 캐시 무효화
        episodeCommentCacheService.evictCommentCache(comment.getEpisodeId());

        // 댓글 삭제(하듣 딜리트)
        episodeCommentRepository.delete(comment);
    }

    // 댓글 목록 조회 (QueryDSL + 캐싱)
    @Transactional(readOnly = true)
    public PageResponse<EpisodeCommentListResponse> getCommentList(
            Long episodeId,
            Pageable pageable
    ) {
        int page = pageable.getPageNumber();

        // 캐시 조회
        PageResponse<EpisodeCommentListResponse> cached =
                episodeCommentCacheService.getCommentCache(episodeId, page);

        if (cached != null) {
            log.debug("[Comment Cache] HIT episodeId={}, page={}", episodeId, page);
            return cached;
        }

        // 캐시 MISS -> DB 조회
        log.debug("[Comment Cache] MISS episodeId={}, page={}", episodeId, page);
        Page<EpisodeCommentListResponse> comments =
                episodeCommentRepository.findCommentList(episodeId, pageable);

        PageResponse<EpisodeCommentListResponse> response = PageResponse.register(comments);

        // 캐시 저장
        episodeCommentCacheService.saveCommentCache(episodeId, page, response);

        return response;
    }
}