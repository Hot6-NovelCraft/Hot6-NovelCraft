package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeStatResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeStatWithViewResponse;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EpisodeStatService {

    private final EpisodeRepository episodeRepository;
    private final NovelRepository novelRepository;
    private final EpisodeCacheService episodeCacheService;

    @Transactional(readOnly = true)
    public List<EpisodeStatWithViewResponse> getEpisodeStats(Long novelId, UserDetailsImpl userDetails) {

        Long authorId = userDetails.getUser().getId();

        // 소설 조회 + 권한 확인
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        if (!Objects.equals(novel.getAuthorId(), authorId)) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_STAT_FORBIDDEN);
        }

        if (novel.isDeleted()) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_ALREADY_DELETED);
        }

        // DB에서 회차별 통계 조회 (JOIN+GROUP BY)
        List<EpisodeStatResponse> stats = episodeRepository.findEpisodeStatsByNovelId(novelId);

        // Redis에서 오늘 조회수 합쳐서 반환
        return stats.stream()
                .map(stat -> new EpisodeStatWithViewResponse(
                        stat.episodeId(),
                        stat.episodeNumber(),
                        stat.title(),
                        episodeCacheService.getEpisodeDailyViewCount(stat.episodeId()),
                        stat.likeCount(),
                        stat.commentCount(),
                        stat.purchaseCount()
                ))
                .toList();
    }
}