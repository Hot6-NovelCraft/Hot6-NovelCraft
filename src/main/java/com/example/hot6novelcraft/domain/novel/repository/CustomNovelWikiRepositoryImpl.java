package com.example.hot6novelcraft.domain.novel.repository;

import com.example.hot6novelcraft.domain.novel.entity.NovelWiki;
import com.example.hot6novelcraft.domain.novel.entity.QNovelWiki;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CustomNovelWikiRepositoryImpl implements CustomNovelWikiRepository {

    private final JPAQueryFactory queryFactory;

    // 소설의 모든 설정집 조회
    @Override
    public List<NovelWiki> findAllByNovelId(Long novelId) {
        QNovelWiki wiki = QNovelWiki.novelWiki;

        return queryFactory
                .selectFrom(wiki)
                .where(wiki.novelId.eq(novelId))
                .orderBy(wiki.category.asc(), wiki.createdAt.asc())
                .fetch();
    }
}