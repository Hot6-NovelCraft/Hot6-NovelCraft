package com.example.hot6novelcraft.domain.mentor.repository;

import com.example.hot6novelcraft.domain.mentor.entity.MentorFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentorFeedbackRepository extends JpaRepository<MentorFeedback, Long> {

    List<MentorFeedback> findAllByMentorshipIdOrderByCreatedAtAsc(Long mentorshipId);

    Optional<MentorFeedback> findTopByMentorshipIdOrderByCreatedAtDesc(Long mentorshipId);
}