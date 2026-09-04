package com.example.hot6novelcraft.domain.mentoring.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentoringExceptionEnum;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentorshipReviewRequest;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.MentorshipReview;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;


import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MentorshipReviewServiceTest {

    @InjectMocks
    private MentorshipReviewService mentorshipReviewService;

    @Mock
    private MentorshipReviewRepository mentorshipReviewRepository;

    @Mock
    private MentorshipRepository mentorshipRepository;

    private static final Long MENTEE_ID = 1L;
    private static final Long MENTORSHIP_ID = 10L;

    private Mentorship mentorship;

    @BeforeEach
    void setUp() {
        mentorship = Mentorship.create(
                2L, MENTEE_ID, 100L, "신청 동기",
                "https://s3.amazonaws.com/test.txt"
        );
        setField(mentorship, "id", MENTORSHIP_ID);
        setField(mentorship, "status", MentorshipStatus.COMPLETED);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("멘토 만족도 평가")
    class CreateReviewTest {

        @Test
        @DisplayName("정상 평가 - 저장 호출")
        void createReview_success() {
            MentorshipReviewRequest request = new MentorshipReviewRequest(5, "정말 도움이 됐습니다!");

            given(mentorshipRepository.findByIdAndMenteeId(MENTORSHIP_ID, MENTEE_ID))
                    .willReturn(Optional.of(mentorship));
            given(mentorshipReviewRepository.existsByMentorshipId(MENTORSHIP_ID))
                    .willReturn(false);

            mentorshipReviewService.createReview(MENTEE_ID, MENTORSHIP_ID, request);

            verify(mentorshipReviewRepository).save(any(MentorshipReview.class));
        }

        @Test
        @DisplayName("멘토링을 찾을 수 없으면 예외")
        void createReview_mentorship_not_found() {
            MentorshipReviewRequest request = new MentorshipReviewRequest(5, "내용");

            given(mentorshipRepository.findByIdAndMenteeId(MENTORSHIP_ID, MENTEE_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorshipReviewService.createReview(MENTEE_ID, MENTORSHIP_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("COMPLETED 상태가 아니면 예외")
        void createReview_not_completed() {
            MentorshipReviewRequest request = new MentorshipReviewRequest(5, "내용");
            setField(mentorship, "status", MentorshipStatus.PENDING);

            given(mentorshipRepository.findByIdAndMenteeId(MENTORSHIP_ID, MENTEE_ID))
                    .willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentorshipReviewService.createReview(MENTEE_ID, MENTORSHIP_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_COMPLETED.getMessage());
        }

        @Test
        @DisplayName("이미 평가한 멘토링이면 예외")
        void createReview_already_exists() {
            MentorshipReviewRequest request = new MentorshipReviewRequest(5, "내용");

            given(mentorshipRepository.findByIdAndMenteeId(MENTORSHIP_ID, MENTEE_ID))
                    .willReturn(Optional.of(mentorship));
            given(mentorshipReviewRepository.existsByMentorshipId(MENTORSHIP_ID))
                    .willReturn(true);

            assertThatThrownBy(() -> mentorshipReviewService.createReview(MENTEE_ID, MENTORSHIP_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_REVIEW_ALREADY_EXISTS.getMessage());
        }
    }
}