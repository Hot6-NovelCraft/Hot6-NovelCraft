package com.example.hot6novelcraft.domain.episode.repository;

import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeStatResponse;
import com.example.hot6novelcraft.domain.episode.entity.QEpisode;
import com.example.hot6novelcraft.domain.episode.entity.QEpisodeComment;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.point.entity.QPointHistory;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CustomEpisodeStatRepositoryImpl implements CustomEpisodeStatRepository {

    private final JPAQueryFactory queryFactory;

    // 회차 통계
    @Override
    public List<EpisodeStatResponse> findEpisodeStatsByNovelId(Long novelId) {

        QEpisode episode = QEpisode.episode;
        QEpisodeComment comment = QEpisodeComment.episodeComment;
        QPointHistory pointHistory = QPointHistory.pointHistory;

        return queryFactory
                .select(Projections.constructor(EpisodeStatResponse.class,
                        episode.id,
                        episode.episodeNumber,
                        episode.title,
                        episode.likeCount,
                        comment.id.countDistinct(),
                        pointHistory.id.countDistinct()
                ))
                .from(episode)
                .leftJoin(comment).on(comment.episodeId.eq(episode.id))
                .leftJoin(pointHistory).on(
                        pointHistory.episodeId.eq(episode.id),
                        pointHistory.type.eq(PointHistoryType.NOVEL)
                )
                .where(
                        episode.novelId.eq(novelId),
                        episode.isDeleted.isFalse(),
                        episode.status.eq(EpisodeStatus.PUBLISHED)
                )
                .groupBy(
                        episode.id,
                        episode.episodeNumber,
                        episode.title,
                        episode.likeCount
                )
                .orderBy(episode.episodeNumber.asc())
                .fetch();
    }
}