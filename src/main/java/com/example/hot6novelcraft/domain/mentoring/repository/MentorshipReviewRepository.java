package com.example.hot6novelcraft.domain.mentoring.repository;

import com.example.hot6novelcraft.domain.mentoring.entity.MentorshipReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MentorshipReviewRepository extends JpaRepository<MentorshipReview, Long> {

    @Query("SELECT AVG(r.rating) FROM MentorshipReview r WHERE r.mentorshipId IN " +
            "(SELECT m.id FROM Mentorship m WHERE m.mentorId = :mentorId)")
    Double findAverageRatingByMentorId(@Param("mentorId") Long mentorId);

    @Query("SELECT COUNT(m) FROM Mentorship m WHERE m.mentorId = :mentorId AND m.status = 'COMPLETED'")
    long countCompletedSessionsByMentorId(@Param("mentorId") Long mentorId);

    @Query("SELECT COUNT(DISTINCT m.menteeId) FROM Mentorship m " +
            "WHERE m.mentorId = :mentorId " +
            "AND m.status IN ('ACCEPTED', 'COMPLETED')")
    long countTotalMenteesByMentorId(@Param("mentorId") Long mentorId);

    // 멘토링 ID로 리뷰 존재 여부 확인 (중복 평가 방지)
    boolean existsByMentorshipId(Long mentorshipId);

    // 여러 멘토링 중 리뷰가 존재하는 ID 목록 (N+1 방지)
    @Query("SELECT r.mentorshipId FROM MentorshipReview r WHERE r.mentorshipId IN :ids")
    List<Long> findReviewedMentorshipIds(@Param("ids") List<Long> ids);

}
