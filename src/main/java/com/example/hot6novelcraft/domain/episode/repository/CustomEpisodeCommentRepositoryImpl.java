package com.example.hot6novelcraft.domain.episode.repository;

import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeCommentListResponse;
import com.example.hot6novelcraft.domain.episode.entity.QEpisodeComment;
import com.example.hot6novelcraft.domain.user.entity.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CustomEpisodeCommentRepositoryImpl implements CustomEpisodeCommentRepository {

    private final JPAQueryFactory queryFactory;

    // 댓글 목록 조회
    @Override
    public Page<EpisodeCommentListResponse> findCommentList(Long episodeId, Pageable pageable) {

        QEpisodeComment comment = QEpisodeComment.episodeComment;
        QUser user = QUser.user;

        List<EpisodeCommentListResponse> content = queryFactory
                .select(Projections.constructor(EpisodeCommentListResponse.class,
                        comment.id,
                        comment.userId,
                        user.nickname,
                        comment.content,
                        comment.createdAt
                ))
                .from(comment)
                .join(user).on(comment.userId.eq(user.id))
                .where(comment.episodeId.eq(episodeId))
                .orderBy(comment.createdAt.desc(), comment.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(comment.count())
                .from(comment)
                .join(user).on(comment.userId.eq(user.id))
                .where(comment.episodeId.eq(episodeId))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }
}