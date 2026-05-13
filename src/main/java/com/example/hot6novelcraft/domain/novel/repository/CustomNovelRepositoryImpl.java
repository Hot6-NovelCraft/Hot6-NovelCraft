package com.example.hot6novelcraft.domain.novel.repository;

import com.example.hot6novelcraft.domain.episode.entity.QEpisode;
import com.example.hot6novelcraft.domain.novel.dto.response.AuthorNovelListResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelDetailResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelListResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.QNovel;
import com.example.hot6novelcraft.domain.novel.entity.enums.MainTag;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.user.entity.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CustomNovelRepositoryImpl implements CustomNovelRepository {

    private final JPAQueryFactory queryFactory;

    // 목록조회(V2)
    @Override
    public Page<NovelListResponse> findNovelListV2(String genre, NovelStatus status, Pageable pageable) {

        QNovel novel = QNovel.novel;
        QUser user = QUser.user;

        List<NovelListResponse> content = queryFactory
                .select(Projections.constructor(NovelListResponse.class,
                        novel.id,
                        novel.title,
                        novel.genre,
                        novel.tags,
                        novel.status,
                        novel.coverImageUrl,
                        novel.viewCount,
                        novel.bookmarkCount,
                        user.nickname
                ))
                .from(novel)
                .join(user).on(novel.authorId.eq(user.id))
                .where(
                        novel.isDeleted.eq(false),
                        novel.status.ne(NovelStatus.PENDING), // PENDING 제외
                        genreEq(genre),
                        statusEq(status)
                )
                .orderBy(novel.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 전체 카운트
        Long total = queryFactory
                .select(novel.count())
                .from(novel)
                .join(user).on(novel.authorId.eq(user.id))
                .where(
                        novel.isDeleted.eq(false),
                        novel.status.ne(NovelStatus.PENDING),
                        genreEq(genre),
                        statusEq(status)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    // 상세조회
    @Override
    public NovelDetailResponse findNovelDetailByNovelId(Long novelId) {

        QNovel novel = QNovel.novel;
        QUser user = QUser.user;

        NovelDetailResponse result = queryFactory
                .select(Projections.constructor(NovelDetailResponse.class,
                        novel.id,
                        novel.title,
                        novel.description,
                        novel.genre,
                        novel.tags,
                        novel.status,
                        novel.coverImageUrl,
                        novel.viewCount,
                        novel.bookmarkCount,
                        user.nickname,
                        user.id,
                        novel.createdAt
                ))
                .from(novel)
                .join(user).on(novel.authorId.eq(user.id))
                .where(
                        novel.id.eq(novelId),
                        novel.isDeleted.eq(false)
                )
                .fetchOne();
        return result;
    }

    // 작가용 소설 목록 조회(작가 에디터용)
    @Override
    public Page<AuthorNovelListResponse> findAuthorNovelList(Long authorId, Pageable pageable) {

        QNovel novel = QNovel.novel;
        QEpisode episode = QEpisode.episode;

        List<AuthorNovelListResponse> content = queryFactory
                .select(Projections.constructor(AuthorNovelListResponse.class,
                        novel.id,
                        novel.title,
                        novel.genre,
                        novel.status,
                        novel.coverImageUrl,

                        // 회차 수 서브쿼리
                        JPAExpressions
                                .select(episode.count())
                                .from(episode)
                                .where(
                                        episode.novelId.eq(novel.id),
                                        episode.isDeleted.eq(false)
                                ),
                        novel.viewCount,
                        novel.bookmarkCount,
                        novel.updatedAt
                ))
                .from(novel)
                .where(
                        novel.authorId.eq(authorId),
                        novel.isDeleted.eq(false)
                )
                .orderBy(novel.updatedAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(novel.count())
                .from(novel)
                .where(
                        novel.authorId.eq(authorId),
                        novel.isDeleted.eq(false)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    // [실시간 랭킹용] 최근 1시간 인기 소설
    @Override
    public List<Novel> findHourlyTopNovels(int limit) {
        QNovel novel = QNovel.novel;

        // 1시간 전 시간 계산
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        return queryFactory
                .selectFrom(novel)
                .where(
                        novel.isDeleted.eq(false),
                        novel.updatedAt.goe(oneHourAgo),
                        novel.status.notIn(NovelStatus.PENDING, NovelStatus.HIATUS),
                        novel.tags.contains(MainTag.ADULT.name()).not()
                )
                .orderBy(novel.viewCount.desc())
                .limit(limit)
                .fetch();
    }

    // [주간 랭킹용] 최근 1주일 인기 소설
    @Override
    public List<Novel> findWeeklyTopNovels(int limit) {
        QNovel novel = QNovel.novel;

        // 1주일 전 시간 계산
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);

        return queryFactory
                .selectFrom(novel)
                .where(
                        novel.isDeleted.eq(false),
                        novel.updatedAt.goe(oneWeekAgo),
                        novel.status.notIn(NovelStatus.PENDING, NovelStatus.HIATUS),
                        novel.tags.contains(MainTag.ADULT.name()).not()
                )
                .orderBy(novel.viewCount.desc())
                .limit(limit)
                .fetch();
    }

    /**
     * 신작용 소설 목록 조회 (한 달 신작 리스트)
     * 서하나
     **/
    @Override
    public List<NovelListResponse> findNewNovelList(String genre, NovelStatus status, int limit) {
        QNovel novel = QNovel.novel;
        QUser user = QUser.user;

        // 한 달 전 날짜 계산
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);

        return queryFactory
                .select(Projections.constructor(NovelListResponse.class,
                        novel.id,
                        novel.title,
                        novel.genre,
                        novel.tags,
                        novel.status,
                        novel.coverImageUrl,
                        novel.viewCount,
                        novel.bookmarkCount,
                        user.nickname
                        ))
                .from(novel)
                .join(user).on(novel.authorId.eq(user.id))
                .where(
                        novel.isDeleted.eq(false)
                        , novel.status.ne(NovelStatus.PENDING)
                        , genreEq(genre)
                        , statusEq(status)
                        , novel.createdAt.goe(oneMonthAgo) // 한 달 이내 신작 소설들
                )
                .orderBy(novel.createdAt.desc())
                .limit(limit)
                .fetch();


    }

    // 장르 필터링 (null이면 조건X)
    private BooleanExpression genreEq(String genre) {
        return genre != null ? QNovel.novel.genre.eq(genre) : null;
    }

    // 상태 필터링 (null이면 조건X)
    private BooleanExpression statusEq(NovelStatus status) {
        return status != null ? QNovel.novel.status.eq(status) : null;
    }
}