package com.example.hot6novelcraft.domain.mentor.repository;

import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentorRepository extends JpaRepository<Mentor, Long>, CustomMentorRepository {

    Optional<Mentor> findByUserId(Long userId);

    boolean existsByUserIdAndStatus(Long userId, MentorStatus status);

    // 배치용 - APPROVED 상태 멘토 청크 조회 (PROFICIENT 제외 - 관리자 수동)
    Page<Mentor> findAllByStatusAndCareerLevelNot(MentorStatus status,
                                                  com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel careerLevel,
                                                  Pageable pageable);

    // 관리자용 - 숙련 (PROFICIENT) 등급 심사 대기 목록 조회
    List<Mentor> findAllByStatusAndCareerLevel(MentorStatus status,
                                               com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel careerLevel);
}