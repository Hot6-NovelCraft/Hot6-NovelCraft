package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.domain.exchange.entity.QWithdrawal;
import com.example.hot6novelcraft.domain.exchange.entity.Withdrawal;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class WithdrawalRepositoryImpl implements WithdrawalRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Withdrawal> findWithFilters(
            Long authorId,
            WithdrawalStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        QWithdrawal withdrawal = QWithdrawal.withdrawal;

        List<Withdrawal> content = queryFactory
                .selectFrom(withdrawal)
                .where(
                        withdrawal.authorId.eq(authorId),
                        statusEq(status),
                        requestedAtGoe(startDate),
                        requestedAtLoe(endDate)
                )
                // [CodeRabbit] tie-breaker 추가 — 동일 시각 레코드 페이지 중복/누락 방지
                .orderBy(withdrawal.requestedAt.desc(), withdrawal.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(withdrawal.count())
                .from(withdrawal)
                .where(
                        withdrawal.authorId.eq(authorId),
                        statusEq(status),
                        requestedAtGoe(startDate),
                        requestedAtLoe(endDate)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<Withdrawal> findAllWithFilters(
            WithdrawalStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        QWithdrawal withdrawal = QWithdrawal.withdrawal;

        List<Withdrawal> content = queryFactory
                .selectFrom(withdrawal)
                .where(
                        statusEq(status),
                        requestedAtGoe(startDate),
                        requestedAtLoe(endDate)
                )
                // [CodeRabbit] tie-breaker 추가 — 동일 시각 레코드 페이지 중복/누락 방지
                .orderBy(withdrawal.requestedAt.desc(), withdrawal.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(withdrawal.count())
                .from(withdrawal)
                .where(
                        statusEq(status),
                        requestedAtGoe(startDate),
                        requestedAtLoe(endDate)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression statusEq(WithdrawalStatus status) {
        return status != null ? QWithdrawal.withdrawal.status.eq(status) : null;
    }

    private BooleanExpression requestedAtGoe(LocalDateTime startDate) {
        return startDate != null ? QWithdrawal.withdrawal.requestedAt.goe(startDate) : null;
    }

    private BooleanExpression requestedAtLoe(LocalDateTime endDate) {
        return endDate != null ? QWithdrawal.withdrawal.requestedAt.loe(endDate) : null;
    }
}