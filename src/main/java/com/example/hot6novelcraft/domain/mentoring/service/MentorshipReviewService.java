package com.example.hot6novelcraft.domain.mentoring.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentoringExceptionEnum;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentorshipReviewRequest;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.MentorshipReview;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MentorshipReviewService {

    private final MentorshipReviewRepository mentorshipReviewRepository;
    private final MentorshipRepository mentorshipRepository;

    @Transactional
    public void createReview(Long userId, Long mentorshipId, MentorshipReviewRequest request) {

        // 멘토링 조회 (본인 멘토링 아니면 NOT_FOUND)
        Mentorship mentorship = mentorshipRepository.findByIdAndMenteeId(mentorshipId, userId)
                .orElseThrow(() -> new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_FOUND));

        // COMPLETED 상태인지 검증
        if (mentorship.getStatus() != MentorshipStatus.COMPLETED) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_NOT_COMPLETED);
        }

        // 이미 평가했는지 검증
        if (mentorshipReviewRepository.existsByMentorshipId(mentorshipId)) {
            throw new ServiceErrorException(MentoringExceptionEnum.MENTORING_REVIEW_ALREADY_EXISTS);
        }

        MentorshipReview review = MentorshipReview.builder()
                .mentorshipId(mentorshipId)
                .reviewerId(userId)
                .rating(request.rating())
                .content(request.content())
                .build();

        mentorshipReviewRepository.save(review);
    }
}
