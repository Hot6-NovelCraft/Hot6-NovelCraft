package com.example.hot6novelcraft.domain.admin.repository;

import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardMentorsStatusResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardNovelStatusResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardUserStatusResponse;
import com.example.hot6novelcraft.domain.mentor.entity.QMentor;
import com.example.hot6novelcraft.domain.novel.entity.QNovel;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.user.entity.QUser;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class CustomAdminRepositoryImpl implements CustomAdminRepository {

    private final JPAQueryFactory queryFactory;

    private final QUser user = QUser.user;
    private final QNovel novel = QNovel.novel;
    private final QMentor mentor = QMentor.mentor;

    /**
     * v1 기본 쿼리 8개
     */

    // 회원
    public Long countTotalUsers() {
        Long result = queryFactory
                .select(user.count())
                .from(user)
                .where(
                        user.isDeleted.eq(false)
                        , user.role.notIn(
                                UserRole.SUPER_ADMIN
                                , UserRole.ADMIN
                                , UserRole.PENDING_ADMIN
                                , UserRole.REJECTED_ADMIN
                        )
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    public Long countNewUsersToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Long result = queryFactory
                .select(user.count())
                .from(user)
                .where(
                        user.isDeleted.eq(false)
                        , user.createdAt.goe(startOfDay)
                        , user.role.notIn(
                                UserRole.SUPER_ADMIN
                                , UserRole.ADMIN
                                , UserRole.PENDING_ADMIN
                                , UserRole.REJECTED_ADMIN
                        )
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    // null 일 경우 전체 조회, role이 있으면 해당 role만 필터링
    public Long countUsersByRole(UserRole role) {
        Long result = queryFactory
                .select(user.count())
                .from(user)
                .where(
                        user.isDeleted.eq(false)
                        , role != null
                                ? user.role.eq(role)
                                : user.role.in(UserRole.READER, UserRole.AUTHOR)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    // 소설
    public Long countTotalNovels(String novelStatus) {
        Long result = queryFactory
                .select(novel.count())
                .from(novel)
                .where(checkNovelStatus(novelStatus))
                .fetchOne();
        return result != null ? result : 0L;
    }

    public Long countNewNovelsToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Long result = queryFactory
                .select(novel.count())
                .from(novel)
                .where(novel.isDeleted.eq(false)
                        , novel.createdAt.goe(startOfDay))
                .fetchOne();
        return result != null ? result : 0L;
    }

    public Long countNovelsByFilter(NovelStatus novelStatusEnum, Boolean isSoftDel) {
        BooleanBuilder builder = new BooleanBuilder();

        // 삭제 소설 조회 요청인 경우
        if (Boolean.TRUE.equals(isSoftDel)) {
            builder.and(novel.isDeleted.eq(true));

        } else {
            // 삭제 안 된 소설
            builder.and(novel.isDeleted.eq(false));

            // novelStatus 있으면 해당 상태만 필터
            if (novelStatusEnum != null) {
                builder.and(novel.status.eq(novelStatusEnum));
            }
        }

        Long result = queryFactory
                .select(novel.count())
                .from(novel)
                .where(builder)
                .fetchOne();
        return result != null ? result : 0L;
    }

    // 멘토
    public Long countTotalMentors() {
        Long result = queryFactory
                .select(mentor.count())
                .from(mentor)
                .fetchOne();
        return result != null ? result : 0L;
    }

    public Long countNewMentorsToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Long result = queryFactory
                .select(mentor.count())
                .from(mentor)
                .where(mentor.createdAt.goe(startOfDay))
                .fetchOne();
        return result != null ? result : 0L;
    }

    /**
     * v2 쿼리 병합
     */
    @Override
    public AdminDashboardUserStatusResponse getIntegratedUserStatus(UserRole role) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return queryFactory
                .select(Projections.constructor(AdminDashboardUserStatusResponse.class
                        , user.count() // 전체 회원
                        , new CaseBuilder()
                                .when(user.createdAt.goe(startOfDay))
                                .then(1L)
                                .otherwise(0L)
                                .sum().coalesce(0L)
                        , new CaseBuilder().when(role != null // 오늘 신규
                                        ? user.role.eq(role)
                                        : user.role.in(UserRole.AUTHOR, UserRole.READER))
                                .then(1L).otherwise(0L)
                                .sum().coalesce(0L) // 필터
                ))
                .from(user)
                .where(user.isDeleted.eq(false) // 제외
                        , user.role.notIn(
                                UserRole.SUPER_ADMIN
                                , UserRole.ADMIN
                                , UserRole.PENDING_ADMIN
                                , UserRole.REJECTED_ADMIN)
                )
                .fetchOne();
    }

    @Override
    public AdminDashboardNovelStatusResponse getIntegratedNovelStatus(String totalStatusFilter, NovelStatus filterStatus, Boolean isSoftDel) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return queryFactory
                .select(Projections.constructor(AdminDashboardNovelStatusResponse.class
                        // 전체 소설 : ALL 이면 통과, 아니면 삭제 안된 것만
                        , new CaseBuilder().when("ALL".equalsIgnoreCase(totalStatusFilter)
                                        ? novel.id.isNotNull()
                                        : novel.isDeleted.eq(false))
                                .then(1L).otherwise(0L)
                                .sum().coalesce(0L)
                        // 오늘 신규 등록
                        , new CaseBuilder().when(novel.isDeleted.eq(false)
                                        .and(novel.createdAt.goe(startOfDay)))
                                .then(1L).otherwise(0L)
                                .sum().coalesce(0L)
                        // 필터 적용 소설
                        , new CaseBuilder().when(filterNovelByCondition(filterStatus, isSoftDel))
                                .then(1L).otherwise(0L)
                                .sum().coalesce(0L)
                ))
                .from(novel)
                .fetchOne();
    }

    // 멘토
    @Override
    public AdminDashboardMentorsStatusResponse getIntegratedMentorsStatus() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return queryFactory
                .select(Projections.constructor(AdminDashboardMentorsStatusResponse.class
                        , mentor.count() // 멘토, 멘티 전체 (status 안나눠져 있음)
                        // 오늘 신규 멘토/멘티
                        , new CaseBuilder()
                                .when(mentor.createdAt.goe(startOfDay))
                                .then(1L).otherwise(0L)
                                .sum().coalesce(0L)
                ))
                .from(mentor)
                .fetchOne();
    }

    /**
     * v3 쿼리 병합 + Redis 신규 라이브 통계
     */
    @Override
    public AdminDashboardUserStatusResponse getUserStatusForeLive(UserRole role) {
        return queryFactory
                .select(Projections.constructor(AdminDashboardUserStatusResponse.class
                        , user.count() // 전체 회원
                        , Expressions.constant(0L)      // newUsersToday placeholder (Redis가 채움)
                        , new CaseBuilder().when(role != null // 오늘 신규
                                        ? user.role.eq(role)
                                        : user.role.in(UserRole.AUTHOR, UserRole.READER))
                                .then(1L).otherwise(0L)
                                .sum().coalesce(0L) // 필터
                ))
                .from(user)
                .where(user.isDeleted.eq(false) // 제외
                        , user.role.notIn(
                                UserRole.SUPER_ADMIN
                                , UserRole.ADMIN
                                , UserRole.PENDING_ADMIN
                                , UserRole.REJECTED_ADMIN)
                )
                .fetchOne();


    }

    @Override
    public AdminDashboardNovelStatusResponse getNovelStatusForLive(String totalStatusFilter, NovelStatus filterStatus, Boolean isSoftDel) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return queryFactory
                .select(Projections.constructor(AdminDashboardNovelStatusResponse.class
                        // 전체 소설 : ALL 이면 통과, 아니면 삭제 안된 것만
                        , new CaseBuilder().when("ALL".equalsIgnoreCase(totalStatusFilter)
                                        ? novel.id.isNotNull()
                                        : novel.isDeleted.eq(false))
                                .then(1L).otherwise(0L)
                                .sum().coalesce(0L)
                        , Expressions.constant(0L)      // newUsersToday placeholder (Redis가 채움)
                        // 필터 적용 소설
                        , new CaseBuilder().when(filterNovelByCondition(filterStatus, isSoftDel))
                                .then(1L).otherwise(0L)
                                .sum().coalesce(0L)
                ))
                .from(novel)
                .fetchOne();
    }

    // 멘토
    @Override
    public AdminDashboardMentorsStatusResponse getMentorsStatusForLive() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return queryFactory
                .select(Projections.constructor(AdminDashboardMentorsStatusResponse.class
                        , mentor.count() // 멘토, 멘티 전체 (status 안나눠져 있음)
                        // 오늘 신규 멘토/멘티
                        , Expressions.constant(0L)      // newUsersToday placeholder (Redis가 채움)
                ))
                .from(mentor)
                .fetchOne();
    }

    /**
     * 공통 메소드
     */

    // V1
    private BooleanExpression checkNovelStatus(String novelStatus) {
        if ("ALL".equalsIgnoreCase(novelStatus)) {
            // 삭제, 보류 상관없이 전부 다 가져옴
            return null;
        }
        // ALL이 아니면 정상 소설만
        return novel.isDeleted.eq(false);
    }

    // V2
    private BooleanExpression filterNovelByCondition(NovelStatus filterStatus, Boolean isSoftDel) {
        // 요청이 isDeleted=true 면 삭제된 소설만
        if (Boolean.TRUE.equals(isSoftDel)) {
            return novel.isDeleted.eq(true);
        }
        // 특정 상태 필터
        if (filterStatus != null) {
            return novel.isDeleted.eq(false)
                    .and(novel.status.eq(filterStatus));
        }
        return novel.isDeleted.eq(false);
    }
}
