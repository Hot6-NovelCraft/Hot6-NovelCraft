package com.example.hot6novelcraft.domain.mentoring.repository;

import com.example.hot6novelcraft.domain.mentor.entity.QMentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorWithNickname;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringReceivedResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorshipHistoryResponse;
import com.example.hot6novelcraft.domain.mentoring.entity.QMentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.novel.entity.QNovel;
import com.example.hot6novelcraft.domain.user.entity.QUser;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RequiredArgsConstructor
public class CustomMentorshipRepositoryImpl implements CustomMentorshipRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<MentorWithNickname> findMentorList(String genre, CareerLevel careerLevel, Long excludeUserId, Pageable pageable) {
        QMentor mentor = QMentor.mentor;
        QUser user = QUser.user;

        BooleanBuilder where = new BooleanBuilder();
        where.and(mentor.status.eq(MentorStatus.APPROVED));

        if (genre != null && !genre.isBlank()) {
            where.and(mentor.mainGenres.contains("\"" + genre + "\""));
        }
        if (careerLevel != null) {
            where.and(mentor.careerLevel.eq(careerLevel));
        }
        if (excludeUserId != null) {
            where.and(mentor.userId.ne(excludeUserId));
        }

        List<MentorWithNickname> content = queryFactory
                .select(Projections.constructor(
                        MentorWithNickname.class,
                        mentor.id,
                        user.nickname,
                        mentor.careerLevel,
                        mentor.mainGenres,
                        mentor.specialFields,
                        mentor.mentoringStyle,
                        mentor.awardsCareer,
                        mentor.maxMentees
                ))
                .from(mentor)
                .join(user).on(user.id.eq(mentor.userId).and(user.isDeleted.eq(false)))
                .where(where)
                .orderBy(mentor.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(mentor.count())
                .from(mentor)
                .join(user).on(user.id.eq(mentor.userId).and(user.isDeleted.eq(false)))
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    @Override
    public List<MentorshipHistoryResponse> findMyHistoryWithMentorNickname(Long menteeId, MentorshipStatus status) {
        QMentorship mentorship = QMentorship.mentorship;
        QMentor mentor = QMentor.mentor;
        QUser user = QUser.user;

        BooleanBuilder where = new BooleanBuilder();
        where.and(mentorship.menteeId.eq(menteeId));
        if (status != null) {
            where.and(mentorship.status.eq(status));
        }

        return queryFactory
                .select(Projections.constructor(
                        MentorshipHistoryResponse.class,
                        mentorship.id,
                        mentorship.mentorId,
                        // null 방어 - 탈퇴한 멘토는 기본값으로 대체
                        Expressions.cases()
                                .when(user.nickname.isNull())
                                .then("알 수 없는 멘토")
                                .otherwise(user.nickname),
                        mentorship.status,
                        mentorship.createdAt,
                        mentorship.motivation,
                        mentorship.manuscriptUrl,
                        mentorship.currentNovelId
                ))
                .from(mentorship)
                .join(mentor).on(mentor.id.eq(mentorship.mentorId))
                .leftJoin(user).on(user.id.eq(mentor.userId)
                        .and(user.isDeleted.eq(false)))
                .where(where)
                .orderBy(mentorship.createdAt.desc())
                .fetch();
    }

    @Override
    public Page<MentoringReceivedResponse> findReceivedMentoringsWithDetails(Long mentorId, Pageable pageable) {
        QMentorship mentorship = QMentorship.mentorship;
        QUser menteeUser = new QUser("menteeUser");
        QNovel novel = QNovel.novel;

        List<MentoringReceivedResponse> content = queryFactory
                .select(Projections.constructor(
                        MentoringReceivedResponse.class,
                        mentorship.id,
                        mentorship.menteeId,
                        // null 방어 - 삭제된 유저/소설은 기본값으로 대체
                        Expressions.cases()
                                .when(menteeUser.nickname.isNull())
                                .then("알 수 없는 사용자")
                                .otherwise(menteeUser.nickname),
                        Expressions.cases()
                                .when(novel.title.isNull())
                                .then("알 수 없는 소설")
                                .otherwise(novel.title),
                        mentorship.createdAt,
                        mentorship.status
                ))
                .from(mentorship)
                .leftJoin(menteeUser).on(menteeUser.id.eq(mentorship.menteeId)
                        .and(menteeUser.isDeleted.eq(false)))
                .leftJoin(novel).on(novel.id.eq(mentorship.currentNovelId)
                        .and(novel.isDeleted.eq(false)))
                .where(mentorship.mentorId.eq(mentorId))
                .orderBy(mentorship.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(mentorship.count())
                .from(mentorship)
                .where(mentorship.mentorId.eq(mentorId))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }
}