package com.example.hot6novelcraft.domain.search.repository;

import com.example.hot6novelcraft.domain.novel.entity.QNovel;
import com.example.hot6novelcraft.domain.novel.entity.enums.MainTag;
import com.example.hot6novelcraft.domain.search.dto.*;
import com.example.hot6novelcraft.domain.user.entity.QAuthorProfile;
import com.example.hot6novelcraft.domain.user.entity.QUser;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.hot6novelcraft.domain.novel.entity.QNovel.novel;

@Repository
@RequiredArgsConstructor
public class CustomSearchRepositoryImpl implements CustomSearchRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * ===============================
     * 소설 검색
     * - 제목에 키워드 포함된 소설 목록
     * - response : 표지, 제목, 작가 닉네임, 장르
     * ===================================
     */
    @Override
    public Page<NovelSearchResponse> searchNovelsByTitle(String keyword, Pageable pageable, boolean isAdult) {
        QNovel novel = QNovel.novel;
        QUser user = QUser.user;

        List<NovelSearchResponse> content = queryFactory
                .select(Projections.constructor(
                        NovelSearchResponse.class
                        , novel.id
                        , novel.coverImageUrl
                        , novel.title
                        , user.nickname
                        , novel.genre
                ))
                .from(novel)
                .join(user).on(novel.authorId.eq(user.id))
                .where(
                        novel.title.containsIgnoreCase(keyword)
                        , novel.isDeleted.eq(false)
                )
                .orderBy(novel.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(novel.count())
                .from(novel)
                .join(user).on(novel.authorId.eq(user.id))
                .where(
                        novel.title.containsIgnoreCase(keyword)
                        , novel.isDeleted.eq(false)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    /**
     * ===============================
     * 태그 검색
     * - 선택한 태그별로 그룹핑해 반환
     * - 프론트에서 enum 영문값(FANTASY, MUNCHKIN)을 그대로 전달해야 함
     * - response : 태그 선택 조합별 필터링
     * - 예시 결과
     * - { tag: "FANTASY", novels: [{소설1}, {소설2}] }
     * - { tag: "MUNCHKIN", novels: [{소설1}, {소설3}] }
     * ===================================
     */
    @Override
    public List<TagGroupSearchResponse> searchNovelsByTags(List<String> tags, boolean isAdult) {
        QNovel novel = QNovel.novel;
        QUser user = QUser.user;

        return tags.stream().map(tag -> {

                    List<NovelSimpleResponse> novels = queryFactory
                            .select(Projections.constructor(
                                    NovelSimpleResponse.class
                                    , novel.id
                                    , novel.title
                                    , user.nickname
                                    , novel.tags
                            ))
                            .from(novel)
                            .join(user).on(novel.authorId.eq(user.id))
                            .where(
                                    novel.tags.containsIgnoreCase(tag)
                                    , novel.isDeleted.eq(false)
                                    , isAdultFilter(isAdult)
                            )
                            .fetch();

                    return new TagGroupSearchResponse(tag, novels);
                })
                .filter(response -> !response.novels().isEmpty())
                .toList();
    }

    /**
     * ===============================
     * 작가 검색 (통합)
     * - 닉네임에 키워드가 포함된 작가들 + 대표작 3개 표시
     * - 제목에 키워드가 포함된 소설 (제목+작가만 간단하게) 표시
     * ===================================
     */
    @Override
    public IntegratedAuthorSearchResponse searchByAuthorKeyword(String keyword, boolean isAdult) {
        QUser user = QUser.user;
        QAuthorProfile authorProfile = QAuthorProfile.authorProfile;
        QNovel novel = QNovel.novel;

        // 닉네임 키워드로 작가 ID+기본 정보 목록 조회
        List<Tuple> authorTuples = queryFactory
                .select(
                        user.id
                        , user.nickname
                        , authorProfile.bio)
                .from(user)
                .join(authorProfile).on(user.id.eq(authorProfile.userId))
                .where(user.nickname.containsIgnoreCase(keyword)
                        , user.isDeleted.eq(false)
                )
                .fetch();

        // 작가 ID 목록 추출
        List<Long> authorIds = authorTuples.stream()
                .map(t -> t.get(user.id))
                .toList();

        List<AuthorSearchResponse> matchingAuthors;

        if (authorIds.isEmpty()) {
            matchingAuthors = List.of();

        } else {

            // 모든 작가 소설 IN 으로 한번에 조회
            List<Tuple> allNovels = queryFactory
                    .select(
                            novel.authorId
                            , novel.id
                            , novel.title
                            , novel.coverImageUrl
                    )
                    .from(novel)
                    .where(
                            novel.authorId.in(authorIds)
                            , novel.isDeleted.eq(false)
                    )
                    .orderBy(novel.viewCount.desc())
                    .limit((long) authorIds.size() * 3)
                    .fetch();

            // 작가별 소설 그룹핑
            Map<Long, List<AuthorSearchResponse.NovelSimple>> novelsByAuthor = allNovels.stream()
                    .collect(Collectors.groupingBy(
                            t -> t.get(novel.authorId)
                            , Collectors.mapping(
                                    t -> new AuthorSearchResponse.NovelSimple(
                                            t.get(novel.id)
                                            , t.get(novel.title)
                                            , t.get(novel.coverImageUrl)
                                    ),
                                    Collectors.toList()
                            )
                    ));

            matchingAuthors = authorTuples.stream()
                    .map(t -> new AuthorSearchResponse(
                            t.get(user.id)
                            , t.get(user.nickname)
                            , t.get(authorProfile.bio)
                            , novelsByAuthor
                            .getOrDefault(t.get(user.id), List.of())
                            .stream()
                            .limit(3)
                            .toList()
                    ))
                    .toList();
        }

        // 제목 키워드 소설 목록
        List<NovelSimpleResponse> matchingNovels = queryFactory
                .select(Projections.constructor(
                        NovelSimpleResponse.class
                        , novel.title
                        , user.nickname
                ))
                .from(novel)
                .join(user).on(novel.authorId.eq(user.id))
                .where(
                        novel.title.containsIgnoreCase(keyword)
                        , novel.isDeleted.eq(false)
                        , isAdultFilter(isAdult)
                )
                .fetch();

        return new IntegratedAuthorSearchResponse(matchingAuthors, matchingNovels);
    }

    private BooleanExpression isAdultFilter(boolean isAdult) {
        if (isAdult) {
            return null;
        }
        return novel.tags.notLike("%" + MainTag.ADULT.name() + "%");
    }
}