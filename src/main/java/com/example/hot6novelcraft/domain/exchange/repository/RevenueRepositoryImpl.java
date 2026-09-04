package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueStatisticsResponse.RevenueStatisticsItem;
import com.example.hot6novelcraft.domain.exchange.entity.QRevenue;
import com.example.hot6novelcraft.domain.exchange.entity.enums.RevenueType;
import com.example.hot6novelcraft.domain.exchange.entity.enums.StatisticsPeriod;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringTemplate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import static com.querydsl.core.types.dsl.Expressions.numberTemplate;
import static com.querydsl.core.types.dsl.Expressions.stringTemplate;

@RequiredArgsConstructor
public class RevenueRepositoryImpl implements RevenueRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<RevenueStatisticsItem> findStatistics(Long authorId, StatisticsPeriod period, Integer year) {
        QRevenue revenue = QRevenue.revenue;

        // 기간별 그룹핑 라벨
        StringTemplate label = period == StatisticsPeriod.MONTHLY
                ? stringTemplate("FUNCTION('DATE_FORMAT', {0}, '%Y-%m')", revenue.createdAt)
                : stringTemplate("CONCAT(FUNCTION('YEAR', {0}), '-W', LPAD(FUNCTION('WEEK', {0}, 1), 2, '0'))", revenue.createdAt, revenue.createdAt);

        // 수익 유형별 조건부 합산
        NumberExpression<Integer> episodeSaleSum = revenue.amount
                .when(0).then(0)  // placeholder
                .otherwise(
                        new com.querydsl.core.types.dsl.CaseBuilder()
                                .when(revenue.type.eq(RevenueType.EPISODE_SALE)).then(revenue.amount)
                                .otherwise(0)
                ).sum();

        NumberExpression<Integer> subscriptionSum = new com.querydsl.core.types.dsl.CaseBuilder()
                .when(revenue.type.eq(RevenueType.SUBSCRIPTION)).then(revenue.amount)
                .otherwise(0)
                .sum();

        List<Tuple> results = queryFactory
                .select(label, episodeSaleSum, subscriptionSum)
                .from(revenue)
                .where(
                        revenue.authorId.eq(authorId),
                        revenue.type.in(RevenueType.EPISODE_SALE, RevenueType.SUBSCRIPTION),
                        numberTemplate(Integer.class, "FUNCTION('YEAR', {0})", revenue.createdAt).eq(year)
                )
                .groupBy(label)
                .orderBy(label.asc())
                .fetch();

        return results.stream()
                .map(tuple -> RevenueStatisticsItem.of(
                        tuple.get(label),
                        tuple.get(episodeSaleSum),
                        tuple.get(subscriptionSum)
                ))
                .collect(Collectors.toList());
    }
}