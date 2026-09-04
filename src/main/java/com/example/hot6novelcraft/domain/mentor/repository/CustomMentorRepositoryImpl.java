package com.example.hot6novelcraft.domain.mentor.repository;

import com.example.hot6novelcraft.domain.mentor.dto.response.MenteeInfoResponse;
import com.example.hot6novelcraft.domain.mentor.entity.QMentorFeedback;
import com.example.hot6novelcraft.domain.mentoring.entity.QMentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.novel.entity.QNovel;
import com.example.hot6novelcraft.domain.user.entity.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CustomMentorRepositoryImpl implements CustomMentorRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<MenteeInfoResponse> findMenteesWithDetails(Long mentorId) {
        QMentorship mentorship = QMentorship.mentorship;
        QUser user = QUser.user;
        QNovel novel = QNovel.novel;
        QMentorFeedback feedback = QMentorFeedback.mentorFeedback;

        return queryFactory
                .select(Projections.constructor(MenteeInfoResponse.class,
                        mentorship.id,
                        mentorship.menteeId,
                        user.nickname,
                        novel.title,
                        mentorship.totalSessions,
                        mentorship.manuscriptDownloadCount,
                        mentorship.status,
                        mentorship.acceptedAt,
                        JPAExpressions
                                .select(feedback.createdAt.max())
                                .from(feedback)
                                .where(feedback.mentorshipId.eq(mentorship.id))
                ))
                .from(mentorship)
                .leftJoin(user).on(user.id.eq(mentorship.menteeId).and(user.isDeleted.eq(false)))
                .leftJoin(novel).on(novel.id.eq(mentorship.currentNovelId))
                .where(
                        mentorship.mentorId.eq(mentorId),
                        mentorship.status.eq(MentorshipStatus.ACCEPTED)
                )
                .fetch();
    }
}