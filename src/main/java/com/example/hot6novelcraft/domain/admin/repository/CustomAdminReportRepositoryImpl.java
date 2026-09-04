package com.example.hot6novelcraft.domain.admin.repository;

import com.example.hot6novelcraft.domain.admin.dto.response.AdminReportListResponse;
import com.example.hot6novelcraft.domain.report.entity.QReport;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportStatus;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;
import com.example.hot6novelcraft.domain.user.entity.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CustomAdminReportRepositoryImpl implements CustomAdminReportRepository {

    private final JPAQueryFactory queryFactory;

    // 신고 목록 조회
    @Override
    public Page<AdminReportListResponse> findReportList(
            ReportStatus status,
            ReportTargetType targetType,
            Pageable pageable
    ) {
        QReport report = QReport.report;
        QUser reporter = new QUser("reporter"); // 신고자
        QUser target = new QUser("target");     // 신고대상

        List<AdminReportListResponse> content = queryFactory
                .select(Projections.constructor(AdminReportListResponse.class,
                        report.id,
                        report.reporterId,
                        reporter.nickname,
                        report.targetId,
                        target.nickname,
                        report.commentId,
                        report.reason,
                        report.status,
                        report.targetType,
                        report.createdAt
                ))
                .from(report)
                .join(reporter).on(report.reporterId.eq(reporter.id))
                .join(target).on(report.targetId.eq(target.id))
                .where(
                        statusEq(status),
                        targetTypeEq(targetType)
                )
                .orderBy(report.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(report.count())
                .from(report)
                .join(reporter).on(report.reporterId.eq(reporter.id))
                .join(target).on(report.targetId.eq(target.id))
                .where(
                        statusEq(status),
                        targetTypeEq(targetType)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    // status 필터 (null이면 전체조회)
    private BooleanExpression statusEq(ReportStatus status) {
        return status != null ? QReport.report.status.eq(status) : null;
    }

    // targetType 필터 (null이면 전체조회)
    private BooleanExpression targetTypeEq(ReportTargetType targetType) {
        return targetType != null ? QReport.report.targetType.eq(targetType) : null;
    }
}