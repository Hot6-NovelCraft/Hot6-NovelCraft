package com.example.hot6novelcraft.domain.mentoring.repository;

import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MentorshipRepository extends JpaRepository<Mentorship, Long>, CustomMentorshipRepository {

    Page<Mentorship> findAllByMentorIdOrderByCreatedAtDesc(Long mentorId, Pageable pageable);

    List<Mentorship> findAllByMentorIdAndStatus(Long mentorId, MentorshipStatus status);

    long countByMentorIdAndStatus(Long mentorId, MentorshipStatus status);

    @Query("SELECT COUNT(m) FROM Mentorship m WHERE m.mentorId = :mentorId AND m.acceptedAt >= :startOfMonth")
    long countAcceptedThisMonth(@Param("mentorId") Long mentorId,
                                @Param("startOfMonth") LocalDateTime startOfMonth);

    @Query("SELECT COUNT(m) FROM Mentorship m WHERE m.mentorId = :mentorId AND m.rejectedAt >= :startOfMonth")
    long countRejectedThisMonth(@Param("mentorId") Long mentorId,
                                @Param("startOfMonth") LocalDateTime startOfMonth);

    // V2: 피드백 작성 시 동시성 보호를 위한 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Mentorship m WHERE m.id = :id")
    Optional<Mentorship> findByIdWithLock(@Param("id") Long id);

    // 멘티가 이미 PENDING,ACCEPTED 멘토링 있는지 확인 (1:1 제약)
    boolean existsByMenteeIdAndStatusIn(Long menteeId, List<MentorshipStatus> statuses);

    // 멘티의 멘토링 이력 조회 (상태 필터)
    List<Mentorship> findAllByMenteeIdAndStatusOrderByCreatedAtDesc(Long menteeId, MentorshipStatus status);

    // 멘티의 멘토링 이력 전체 조회
    List<Mentorship> findAllByMenteeIdOrderByCreatedAtDesc(Long menteeId);

    // 멘토링 ID와 멘티 ID로 조회 (본인 멘토링 검증)
    Optional<Mentorship> findByIdAndMenteeId(Long id, Long menteeId);

}