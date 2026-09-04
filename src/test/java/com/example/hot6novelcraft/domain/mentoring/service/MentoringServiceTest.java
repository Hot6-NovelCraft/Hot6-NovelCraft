package com.example.hot6novelcraft.domain.mentoring.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentoringExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.domain.file.service.FileUploadService;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.MentorFeedback;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorFeedbackRepository;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentoringFeedbackRequest;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringDetailResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringFeedbackResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringReceivedResponse;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentoringServiceTest {

    @InjectMocks
    private MentoringService mentoringService;

    @Mock private FileUploadService fileUploadService;
    @Mock private MentorFeedbackRepository mentorFeedbackRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private MentorRepository mentorRepository;
    @Mock private UserRepository userRepository;
    @Mock private NovelRepository novelRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private static final Long USER_ID          = 1L;
    private static final Long MENTOR_ENTITY_ID = 5L;
    private static final Long MENTEE_ID        = 501L;
    private static final Long MENTORING_ID     = 10L;
    private static final Long NOVEL_ID         = 100L;
    private static final Long OTHER_USER_ID    = 999L;

    private Mentorship mentorship;
    private Mentor mentor;
    private User mentorUser;
    private User menteeUser;
    private Novel novel;

    @BeforeEach
    void setUp() {
        mentorship = Mentorship.create(
                MENTOR_ENTITY_ID, MENTEE_ID, NOVEL_ID,
                "신청 동기입니다", "https://s3.amazonaws.com/file.pdf"
        );
        setField(mentorship, "id", MENTORING_ID);

        mentor = Mentor.create(
                USER_ID, CareerLevel.INTRODUCTION,
                "[\"판타지\"]", "[\"문장력\"]", "[\"꼼꼼한 피드백형\"]",
                "소개글", "수상경력", 3, true, "멘티 설명", MentorStatus.APPROVED
        );
        setField(mentor, "id", MENTOR_ENTITY_ID);

        mentorUser = User.builder()
                .email("mentor@test.com").password("pw").nickname("김철수").role(UserRole.AUTHOR).build();

        menteeUser = User.builder()
                .email("mentee@test.com").password("pw").nickname("홍길동").role(UserRole.AUTHOR).build();

        novel = Novel.createNovel(USER_ID, "자바 백엔드 로드맵", "설명", "판타지", "태그");
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
    @DisplayName("내 멘토링 접수 목록 조회")
    class GetReceivedMentoringsTest {

        @Test
        @DisplayName("정상 조회 - menteeName, title 정상 반환")
        void getReceivedMentorings_success() {
            PageImpl<Mentorship> page = new PageImpl<>(List.of(mentorship));
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findAllByMentorIdOrderByCreatedAtDesc(eq(MENTOR_ENTITY_ID), any()))
                    .willReturn(page);
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.of(novel));

            Page<MentoringReceivedResponse> result =
                    mentoringService.getReceivedMentorings(USER_ID, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).menteeName()).isEqualTo("홍길동");
            assertThat(result.getContent().get(0).title()).isEqualTo("자바 백엔드 로드맵");
            assertThat(result.getContent().get(0).status()).isEqualTo(MentorshipStatus.PENDING);
        }

        @Test
        @DisplayName("멘티가 탈퇴한 경우 알 수 없는 사용자 반환")
        void getReceivedMentorings_deleted_mentee() {
            PageImpl<Mentorship> page = new PageImpl<>(List.of(mentorship));
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findAllByMentorIdOrderByCreatedAtDesc(eq(MENTOR_ENTITY_ID), any()))
                    .willReturn(page);
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.empty());
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.of(novel));

            Page<MentoringReceivedResponse> result =
                    mentoringService.getReceivedMentorings(USER_ID, PageRequest.of(0, 10));

            assertThat(result.getContent().get(0).menteeName()).isEqualTo("알 수 없는 사용자");
        }

        @Test
        @DisplayName("소설이 삭제된 경우 알 수 없는 소설 반환")
        void getReceivedMentorings_deleted_novel() {
            PageImpl<Mentorship> page = new PageImpl<>(List.of(mentorship));
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findAllByMentorIdOrderByCreatedAtDesc(eq(MENTOR_ENTITY_ID), any()))
                    .willReturn(page);
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.empty());

            Page<MentoringReceivedResponse> result =
                    mentoringService.getReceivedMentorings(USER_ID, PageRequest.of(0, 10));

            assertThat(result.getContent().get(0).title()).isEqualTo("알 수 없는 소설");
        }

        @Test
        @DisplayName("빈 목록 조회")
        void getReceivedMentorings_empty() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findAllByMentorIdOrderByCreatedAtDesc(eq(MENTOR_ENTITY_ID), any()))
                    .willReturn(new PageImpl<>(List.of()));

            Page<MentoringReceivedResponse> result =
                    mentoringService.getReceivedMentorings(USER_ID, PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void getReceivedMentorings_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.getReceivedMentorings(USER_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("멘티 수락")
    class AcceptMenteeTest {

        @Test
        @DisplayName("정상 수락 - 슬롯 차감 및 상태 변경")
        void acceptMentee_success() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, USER_ID);

            assertThat(mentorship.getStatus()).isEqualTo(MentorshipStatus.ACCEPTED);
            assertThat(mentor.getMaxMentees()).isEqualTo(2);
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void acceptMentee_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void acceptMentee_mentoring_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void acceptMentee_unauthorized_throws() {
            Mentor otherMentor = Mentor.create(
                    OTHER_USER_ID, CareerLevel.INTRODUCTION,
                    "[]", "[]", "[]", "소개", null, 3, true, "설명", MentorStatus.APPROVED
            );
            setField(otherMentor, "id", 999L);

            given(mentorRepository.findByUserId(OTHER_USER_ID)).willReturn(Optional.of(otherMentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, OTHER_USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("멘티 정보 불일치 시 예외 발생")
        void acceptMentee_mentee_not_match_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, 999L, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_MENTEE_NOT_MATCH.getMessage());
        }

        @Test
        @DisplayName("이미 처리된 멘토링이면 예외 발생")
        void acceptMentee_already_processed_throws() {
            mentorship.approve();
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.acceptMentee(MENTORING_ID, MENTEE_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_ALREADY_PROCESSED.getMessage());
        }
    }

    @Nested
    @DisplayName("멘티 거절")
    class RejectMenteeTest {

        @Test
        @DisplayName("정상 거절 - 상태 변경")
        void rejectMentee_success() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            mentoringService.rejectMentee(MENTORING_ID, MENTEE_ID, USER_ID);

            assertThat(mentorship.getStatus()).isEqualTo(MentorshipStatus.REJECTED);
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void rejectMentee_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.rejectMentee(MENTORING_ID, MENTEE_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void rejectMentee_mentoring_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.rejectMentee(MENTORING_ID, MENTEE_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void rejectMentee_unauthorized_throws() {
            Mentor otherMentor = Mentor.create(
                    OTHER_USER_ID, CareerLevel.INTRODUCTION,
                    "[]", "[]", "[]", "소개", null, 3, true, "설명", MentorStatus.APPROVED
            );
            setField(otherMentor, "id", 999L);

            given(mentorRepository.findByUserId(OTHER_USER_ID)).willReturn(Optional.of(otherMentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.rejectMentee(MENTORING_ID, MENTEE_ID, OTHER_USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("멘티 정보 불일치 시 예외 발생")
        void rejectMentee_mentee_not_match_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.rejectMentee(MENTORING_ID, 999L, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_MENTEE_NOT_MATCH.getMessage());
        }

        @Test
        @DisplayName("이미 처리된 멘토링이면 예외 발생")
        void rejectMentee_already_processed_throws() {
            mentorship.reject();
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.rejectMentee(MENTORING_ID, MENTEE_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_ALREADY_PROCESSED.getMessage());
        }
    }

    @Nested
    @DisplayName("원고 다운로드 URL 조회")
    class GetManuscriptDownloadUrlTest {

        @Test
        @DisplayName("정상 조회 - URL 반환 및 다운로드 횟수 증가")
        void getManuscriptDownloadUrl_success() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(fileUploadService.generateManuscriptPresignedUrl(any()))
                    .willReturn("https://s3.amazonaws.com/file.pdf");  // 추가

            String url = mentoringService.getManuscriptDownloadUrl(MENTORING_ID, USER_ID);

            assertThat(url).isEqualTo("https://s3.amazonaws.com/file.pdf");
            assertThat(mentorship.getManuscriptDownloadCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void getManuscriptDownloadUrl_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.getManuscriptDownloadUrl(MENTORING_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void getManuscriptDownloadUrl_mentoring_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.getManuscriptDownloadUrl(MENTORING_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void getManuscriptDownloadUrl_unauthorized_throws() {
            Mentor otherMentor = Mentor.create(
                    OTHER_USER_ID, CareerLevel.INTRODUCTION,
                    "[]", "[]", "[]", "소개", null, 3, true, "설명", MentorStatus.APPROVED
            );
            setField(otherMentor, "id", 999L);

            given(mentorRepository.findByUserId(OTHER_USER_ID)).willReturn(Optional.of(otherMentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.getManuscriptDownloadUrl(MENTORING_ID, OTHER_USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("원고 URL이 없으면 예외 발생")
        void getManuscriptDownloadUrl_manuscript_not_found_throws() {
            Mentorship noFileMentorship = Mentorship.create(
                    MENTOR_ENTITY_ID, MENTEE_ID, NOVEL_ID, "신청 동기", null
            );
            setField(noFileMentorship, "id", MENTORING_ID);

            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(noFileMentorship));

            assertThatThrownBy(() -> mentoringService.getManuscriptDownloadUrl(MENTORING_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_MANUSCRIPT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("멘토링 종료")
    class CompleteMentoringTest {

        @Test
        @DisplayName("정상 종료 - 슬롯 반환 및 상태 변경")
        void completeMentoring_success() {
            mentorship.approve();
            mentor.decreaseSlot();
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            mentoringService.completeMentoring(MENTORING_ID, USER_ID);

            assertThat(mentorship.getStatus()).isEqualTo(MentorshipStatus.COMPLETED);
            assertThat(mentor.getMaxMentees()).isEqualTo(3);
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void completeMentoring_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.completeMentoring(MENTORING_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void completeMentoring_mentoring_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.completeMentoring(MENTORING_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void completeMentoring_unauthorized_throws() {
            Mentor otherMentor = Mentor.create(
                    OTHER_USER_ID, CareerLevel.INTRODUCTION,
                    "[]", "[]", "[]", "소개", null, 3, true, "설명", MentorStatus.APPROVED
            );
            setField(otherMentor, "id", 999L);

            given(mentorRepository.findByUserId(OTHER_USER_ID)).willReturn(Optional.of(otherMentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.completeMentoring(MENTORING_ID, OTHER_USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("진행 중이 아닌 멘토링 종료 시 예외 발생")
        void completeMentoring_not_accepted_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.completeMentoring(MENTORING_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_ACCEPTED.getMessage());
        }
    }

    @Nested
    @DisplayName("멘토링 상세 정보 조회")
    class GetMentoringDetailTest {

        @Test
        @DisplayName("정상 조회 - 멘토링 상세 정보 반환")
        void getMentoringDetail_success() {
            MentorFeedback feedback = MentorFeedback.create(
                    MENTORING_ID, MENTOR_ENTITY_ID, "1회차 피드백", 1, "ERD 설계 및 API 명세 작성"
            );
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(USER_ID)).willReturn(Optional.of(mentorUser));
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.of(novel));
            given(mentorFeedbackRepository.findAllByMentorshipIdOrderByCreatedAtAsc(MENTORING_ID))
                    .willReturn(List.of(feedback));

            MentoringDetailResponse response = mentoringService.getMentoringDetail(MENTORING_ID, USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.novelTitle()).isEqualTo("자바 백엔드 로드맵");
            assertThat(response.mentorName()).isEqualTo("김철수");
            assertThat(response.menteeName()).isEqualTo("홍길동");
            assertThat(response.status()).isEqualTo(MentorshipStatus.PENDING);
            assertThat(response.feedbacks()).hasSize(1);
            assertThat(response.feedbacks().get(0).title()).isEqualTo("1회차 피드백");
            assertThat(response.feedbacks().get(0).sessionNumber()).isEqualTo(1);
            assertThat(response.feedbacks().get(0).content()).isEqualTo("ERD 설계 및 API 명세 작성");
        }

        @Test
        @DisplayName("피드백이 없는 경우 빈 리스트 반환")
        void getMentoringDetail_no_feedbacks() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(USER_ID)).willReturn(Optional.of(mentorUser));
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.of(novel));
            given(mentorFeedbackRepository.findAllByMentorshipIdOrderByCreatedAtAsc(MENTORING_ID))
                    .willReturn(List.of());

            MentoringDetailResponse response = mentoringService.getMentoringDetail(MENTORING_ID, USER_ID);

            assertThat(response.feedbacks()).isEmpty();
            assertThat(response.totalSessions()).isEqualTo(0);
        }

        @Test
        @DisplayName("멘토가 탈퇴한 경우 알 수 없는 사용자 반환")
        void getMentoringDetail_deleted_mentor() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(USER_ID)).willReturn(Optional.empty());
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.of(novel));
            given(mentorFeedbackRepository.findAllByMentorshipIdOrderByCreatedAtAsc(MENTORING_ID))
                    .willReturn(List.of());

            MentoringDetailResponse response = mentoringService.getMentoringDetail(MENTORING_ID, USER_ID);

            assertThat(response.mentorName()).isEqualTo("알 수 없는 사용자");
        }

        @Test
        @DisplayName("멘티가 탈퇴한 경우 알 수 없는 사용자 반환")
        void getMentoringDetail_deleted_mentee() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(USER_ID)).willReturn(Optional.of(mentorUser));
            given(userRepository.findByIdAndIsDeletedFalse(MENTEE_ID)).willReturn(Optional.empty());
            given(novelRepository.findById(NOVEL_ID)).willReturn(Optional.of(novel));
            given(mentorFeedbackRepository.findAllByMentorshipIdOrderByCreatedAtAsc(MENTORING_ID))
                    .willReturn(List.of());

            MentoringDetailResponse response = mentoringService.getMentoringDetail(MENTORING_ID, USER_ID);

            assertThat(response.menteeName()).isEqualTo("알 수 없는 사용자");
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void getMentoringDetail_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.getMentoringDetail(MENTORING_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void getMentoringDetail_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.getMentoringDetail(MENTORING_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void getMentoringDetail_unauthorized_throws() {
            Mentor otherMentor = Mentor.create(
                    OTHER_USER_ID, CareerLevel.INTRODUCTION,
                    "[]", "[]", "[]", "소개", null, 3, true, "설명", MentorStatus.APPROVED
            );
            setField(otherMentor, "id", 999L);

            given(mentorRepository.findByUserId(OTHER_USER_ID)).willReturn(Optional.of(otherMentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.getMentoringDetail(MENTORING_ID, OTHER_USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }
    }

    @Nested
    @DisplayName("멘토링 피드백 작성")
    class CreateFeedbackTest {

        @Test
        @DisplayName("정상 피드백 작성 - 세션 증가 및 피드백 저장")
        void createFeedback_success() {
            mentorship.approve();

            MentoringFeedbackRequest request = new MentoringFeedbackRequest(
                    "1회차 피드백",
                    "ERD 설계 및 API 명세 작성"
            );
            MentorFeedback feedback = MentorFeedback.create(
                    MENTORING_ID, MENTOR_ENTITY_ID, "1회차 피드백", 1, "ERD 설계 및 API 명세 작성"
            );

            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));
            given(mentorFeedbackRepository.save(any())).willReturn(feedback);

            MentoringFeedbackResponse response =
                    mentoringService.createFeedback(MENTORING_ID, USER_ID, request);

            ArgumentCaptor<MentorFeedback> captor = ArgumentCaptor.forClass(MentorFeedback.class);
            verify(mentorFeedbackRepository, times(1)).save(captor.capture());
            MentorFeedback saved = captor.getValue();

            assertThat(response).isNotNull();
            assertThat(response.title()).isEqualTo("1회차 피드백");
            assertThat(response.sessionNumber()).isEqualTo(1);
            assertThat(response.content()).isEqualTo("ERD 설계 및 API 명세 작성");
            assertThat(mentorship.getTotalSessions()).isEqualTo(1);
            assertThat(saved.getMentorshipId()).isEqualTo(MENTORING_ID);
            assertThat(saved.getAuthorId()).isEqualTo(MENTOR_ENTITY_ID);
            assertThat(saved.getTitle()).isEqualTo("1회차 피드백");
            assertThat(saved.getSessionNumber()).isEqualTo(1);
            assertThat(saved.getContent()).isEqualTo("ERD 설계 및 API 명세 작성");
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void createFeedback_mentor_not_found_throws() {
            MentoringFeedbackRequest request = new MentoringFeedbackRequest(
                    "1회차 피드백", "ERD 설계 및 API 명세 작성"
            );
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.createFeedback(MENTORING_ID, USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("멘토링이 없으면 예외 발생")
        void createFeedback_mentoring_not_found_throws() {
            MentoringFeedbackRequest request = new MentoringFeedbackRequest(
                    "1회차 피드백", "ERD 설계 및 API 명세 작성"
            );
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentoringService.createFeedback(MENTORING_ID, USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없으면 예외 발생")
        void createFeedback_unauthorized_throws() {
            MentoringFeedbackRequest request = new MentoringFeedbackRequest(
                    "1회차 피드백", "ERD 설계 및 API 명세 작성"
            );
            Mentor otherMentor = Mentor.create(
                    OTHER_USER_ID, CareerLevel.INTRODUCTION,
                    "[]", "[]", "[]", "소개", null, 3, true, "설명", MentorStatus.APPROVED
            );
            setField(otherMentor, "id", 999L);

            given(mentorRepository.findByUserId(OTHER_USER_ID)).willReturn(Optional.of(otherMentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.createFeedback(MENTORING_ID, OTHER_USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("진행 중이 아닌 멘토링에 피드백 작성 시 예외 발생")
        void createFeedback_not_accepted_throws() {
            MentoringFeedbackRequest request = new MentoringFeedbackRequest(
                    "1회차 피드백", "ERD 설계 및 API 명세 작성"
            );
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findById(MENTORING_ID)).willReturn(Optional.of(mentorship));

            assertThatThrownBy(() -> mentoringService.createFeedback(MENTORING_ID, USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_FEEDBACK_ONLY_ACCEPTED.getMessage());
        }
    }
}