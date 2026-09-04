package com.example.hot6novelcraft.domain.library.repository;

import com.example.hot6novelcraft.domain.library.entity.Library;
import com.example.hot6novelcraft.domain.library.entity.enums.LibraryType;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.hot6novelcraft.domain.library.entity.QLibrary.library;

@Repository
@RequiredArgsConstructor
public class LibraryQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<Library> findByUserIdWithSort(Long userId, LibraryType libraryType,
                                              String sort, Pageable pageable) {

        BooleanExpression condition = library.userId.eq(userId)
                .and(typeFilter(libraryType));

        List<Library> content = queryFactory
                .selectFrom(library)
                .where(condition)
                .orderBy(resolveOrder(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(library.count())
                .from(library)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    // null이면 전체(ALL) 조회
    private BooleanExpression typeFilter(LibraryType libraryType) {
        return libraryType != null ? library.libraryType.eq(libraryType) : null;
    }

    private OrderSpecifier<?> resolveOrder(String sort) {
        if ("TITLE".equalsIgnoreCase(sort)) {
            return library.novelTitle.asc();
        }
        return library.createdAt.desc();
    }
}
