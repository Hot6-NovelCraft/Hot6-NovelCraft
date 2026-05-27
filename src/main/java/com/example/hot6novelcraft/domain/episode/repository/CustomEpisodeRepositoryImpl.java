package com.example.hot6novelcraft.domain.episode.repository;

import com.example.hot6novelcraft.domain.episode.dto.cache.EpisodeContentCache;
import com.example.hot6novelcraft.domain.episode.dto.response.AuthorEpisodeListResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeListResponse;
import com.example.hot6novelcraft.domain.episode.entity.QEpisode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.hot6novelcraft.domain.episode.entity.QEpisode.episode;

@Repository
@RequiredArgsConstructor
public class CustomEpisodeRepositoryImpl implements CustomEpisodeRepository {

    private final JPAQueryFactory queryFactory;

    // 회차 목록 조회
    @Override
    public Page<EpisodeListResponse> findEpisodeListByNovelId(Long novelId, Pageable pageable) {

        QEpisode episode = QEpisode.episode;

        List<EpisodeListResponse> content = queryFactory
                .select(Projections.constructor(EpisodeListResponse.class,
                        episode.id,
                        episode.episodeNumber,
                        episode.title,
                        episode.isFree,
                        episode.pointPrice,
                        episode.likeCount,
                        episode.publishedAt
                ))
                .from(episode)
                .where(
                        episode.novelId.eq(novelId),
                        episode.status.eq(EpisodeStatus.PUBLISHED), // 발행한것만
                        episode.isDeleted.eq(false) // 삭제 된건지 확인
                )
                .orderBy(episode.episodeNumber.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(episode.count())
                .from(episode)
                .where(
                        episode.novelId.eq(novelId),
                        episode.status.eq(EpisodeStatus.PUBLISHED),
                        episode.isDeleted.eq(false)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    // 회차 본문조회 단건
    @Override
    public EpisodeContentCache findContentCacheById(Long episodeId) {
        return queryFactory
                .select(Projections.constructor(EpisodeContentCache.class,
                        episode.id,
                        episode.novelId,
                        episode.episodeNumber,
                        episode.title,
                        episode.content,
                        episode.likeCount,
                        episode.isFree,
                        episode.pointPrice
                ))
                .from(episode)
                .where(
                        episode.id.eq(episodeId),
                        episode.isDeleted.isFalse(),
                        episode.status.eq(EpisodeStatus.PUBLISHED)
                )
                .fetchOne();
    }

    // 작가용 회차 목록 조회 (본인 소설의 회차, DRAFT 포함)
    @Override
    public Page<AuthorEpisodeListResponse> findAuthorEpisodeList(Long novelId, Pageable pageable) {

        List<AuthorEpisodeListResponse> content = queryFactory
                .select(Projections.constructor(AuthorEpisodeListResponse.class,
                        episode.id,
                        episode.episodeNumber,
                        episode.title,
                        episode.status,
                        episode.isFree,
                        episode.pointPrice,
                        episode.publishedAt,
                        episode.updatedAt
                ))
                .from(episode)
                .where(
                        episode.novelId.eq(novelId),
                        episode.isDeleted.eq(false)
                )
                .orderBy(episode.episodeNumber.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(episode.count())
                .from(episode)
                .where(
                        episode.novelId.eq(novelId),
                        episode.isDeleted.eq(false)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }
}