package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EpisodeExceptionEnum;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeLikeResponse;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.EpisodeLike;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeLikeRepository;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EpisodeLikeService {

    private final EpisodeLikeRepository episodeLikeRepository;
    private final EpisodeRepository episodeRepository;

    // 회차 좋아요 (좋아요,취소)
    @Transactional
    public EpisodeLikeResponse toggleLike(Long episodeId, UserDetailsImpl userDetails) {

        Long userId = userDetails.getUser().getId();

        // 회차 조회
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));

        // 삭제된 회차 체크
        if (episode.isDeleted()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_DELETED);
        }

        // 발행된 회차인지 확인
        if (episode.getStatus() != EpisodeStatus.PUBLISHED) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_PUBLISHED);
        }

        // 좋아요 여부 확인
        Optional<EpisodeLike> existing = episodeLikeRepository
                .findByUserIdAndEpisodeId(userId, episodeId);

        // 이미 좋아요 -> 취소
        if (existing.isPresent()) {
            episodeLikeRepository.delete(existing.get());
            episodeRepository.decrementLikeCount(episodeId);

            // 최신 카운트 조회
            Episode updated = episodeRepository.findById(episodeId)
                    .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));
            return EpisodeLikeResponse.of(false, updated.getLikeCount());
        }

        // 좋아요 생성
        EpisodeLike like = EpisodeLike.builder()
                .userId(userId)
                .episodeId(episodeId)
                .build();
        episodeLikeRepository.save(like);
        episodeRepository.incrementLikeCount(episodeId);

        // 최신 카운트 조회
        Episode updated = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));
        return EpisodeLikeResponse.of(true, updated.getLikeCount());
    }
}