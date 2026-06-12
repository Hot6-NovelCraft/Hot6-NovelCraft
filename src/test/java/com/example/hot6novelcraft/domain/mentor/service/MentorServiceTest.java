package com.example.hot6novelcraft.domain.mentor.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.domain.admin.service.AdminCacheService;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorRegisterRequest;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorUpdateRequest;
import com.example.hot6novelcraft.domain.mentor.dto.response.*;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.MentorFeedback;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorFeedbackRepository;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipReviewRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorServiceTest {

    @InjectMocks
    private MentorService mentorService;

    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private MentorFeedbackRepository mentorFeedbackRepository;
    @Mock private UserRepository userRepository;
    @Mock private NovelRepository novelRepository;
    @Mock private MentorRepository mentorRepository;
    @Mock private EpisodeRepository episodeRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private MentorshipReviewRepository mentorshipReviewRepository;
    @Mock private AdminCacheService adminCacheService;

    private static final Long USER_ID = 1L;

    private MentorRegisterRequest registerRequest;
    private MentorUpdateRequest updateRequest;
    private Mentor mentor;

    @BeforeEach
    void setUp() {
        registerRequest = new MentorRegisterRequest(
                "판타지 장르를 10년째 쓰고 있습니다",
                List.of("판타지", "로판"),
                List.of("문장력", "플롯"),
                CareerLevel.INTRODUCTION,
                "2022 웹소설 신인상 수상",
                List.of("꼼꼼한 피드백형"),
                3,
                true,
                "연재 의지가 강한 분을 환영합니다"
        );

        updateRequest = new MentorUpdateRequest(
                "수정된 소개글입니다. 판타지 장르 10년차입니다",
                List.of("판타지"),
                List.of("문장력"),
                "2024년 공모전 대상 수상",
                List.of("방향 제시형"),
                5,
                false,
                "성실한 분 환영합니다"
        );

        mentor = Mentor.create(
                USER_ID,
                CareerLevel.INTRODUCTION,
                "[\"판타지\"]",
                "[\"문장력\"]",
                "[\"꼼꼼한 피드백형\"]",
                "판타지 장르를 10년째 쓰고 있습니다",
                "2022 웹소설 신인상 수상",
                3,
                true,
                "연재 의지가 강한 분을 환영합니다",
                MentorStatus.PENDING
        );
    }

    // ===================== register 테스트 =====================

    @Nested
    @DisplayName("멘토 등록 신청")
    class RegisterTest {

        @Test
        @DisplayName("INTRODUCTION 등급 - 조건 미달 시 PENDING으로 저장")
        void register_introduction_pending() throws Exception {
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.PENDING)).willReturn(false);
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.APPROVED)).willReturn(false);
            given(novelRepository.findNovelIdsByAuthorId(USER_ID)).willReturn(List.of(1L));
            given(episodeRepository.countByNovelIdInAndStatus(any(), eq(EpisodeStatus.PUBLISHED))).willReturn(10L);
            given(objectMapper.writeValueAsString(any())).willReturn("[\"판타지\"]");

            ArgumentCaptor<Mentor> captor = ArgumentCaptor.forClass(Mentor.class);
            given(mentorRepository.save(captor.capture())).willReturn(mentor);

            mentorService.register(USER_ID, registerRequest);

            Mentor savedMentor = captor.getValue();
            assertThat(savedMentor.getStatus()).isEqualTo(MentorStatus.PENDING);
            assertThat(savedMentor.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("INTRODUCTION 등급 - 조건 충족 시 APPROVED로 저장")
        void register_introduction_approved() throws Exception {
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.PENDING)).willReturn(false);
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.APPROVED)).willReturn(false);
            given(novelRepository.findNovelIdsByAuthorId(USER_ID)).willReturn(List.of(1L));
            given(episodeRepository.countByNovelIdInAndStatus(any(), eq(EpisodeStatus.PUBLISHED))).willReturn(50L);
            given(objectMapper.writeValueAsString(any())).willReturn("[\"판타지\"]");

            ArgumentCaptor<Mentor> captor = ArgumentCaptor.forClass(Mentor.class);
            given(mentorRepository.save(captor.capture())).willReturn(mentor);

            mentorService.register(USER_ID, registerRequest);

            assertThat(captor.getValue().getStatus()).isEqualTo(MentorStatus.APPROVED);
        }

        @Test
        @DisplayName("ELEMENTARY 등급 - 에피소드 50개 이상 + 좋아요 50개 이상 시 APPROVED")
        void register_elementary_approved() throws Exception {
            MentorRegisterRequest elementaryRequest = new MentorRegisterRequest(
                    "판타지 장르를 10년째 쓰고 있습니다",
                    List.of("판타지"),
                    List.of("문장력", "플롯"),
                    CareerLevel.ELEMENTARY,
                    null, null, 3, true, null
            );

            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.PENDING)).willReturn(false);
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.APPROVED)).willReturn(false);
            given(novelRepository.findNovelIdsByAuthorId(USER_ID)).willReturn(List.of(1L));
            given(episodeRepository.countByNovelIdInAndStatus(any(), eq(EpisodeStatus.PUBLISHED))).willReturn(50L);
            given(episodeRepository.sumLikeCountByNovelIdIn(any())).willReturn(50L);
            given(objectMapper.writeValueAsString(any())).willReturn("[\"판타지\"]");

            ArgumentCaptor<Mentor> captor = ArgumentCaptor.forClass(Mentor.class);
            given(mentorRepository.save(captor.capture())).willReturn(mentor);

            mentorService.register(USER_ID, elementaryRequest);

            assertThat(captor.getValue().getStatus()).isEqualTo(MentorStatus.APPROVED);
        }

        @Test
        @DisplayName("INTERMEDIATE 등급 - 에피소드 100개 이상 + 좋아요 100개 이상 시 APPROVED")
        void register_intermediate_approved() throws Exception {
            MentorRegisterRequest intermediateRequest = new MentorRegisterRequest(
                    "판타지 장르를 10년째 쓰고 있습니다",
                    List.of("판타지"),
                    List.of("문장력", "플롯"),
                    CareerLevel.INTERMEDIATE,
                    null, null, 3, true, null
            );

            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.PENDING)).willReturn(false);
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.APPROVED)).willReturn(false);
            given(novelRepository.findNovelIdsByAuthorId(USER_ID)).willReturn(List.of(1L));
            given(episodeRepository.countByNovelIdInAndStatus(any(), eq(EpisodeStatus.PUBLISHED))).willReturn(100L);
            given(episodeRepository.sumLikeCountByNovelIdIn(any())).willReturn(100L);
            given(objectMapper.writeValueAsString(any())).willReturn("[\"판타지\"]");

            ArgumentCaptor<Mentor> captor = ArgumentCaptor.forClass(Mentor.class);
            given(mentorRepository.save(captor.capture())).willReturn(mentor);

            mentorService.register(USER_ID, intermediateRequest);

            assertThat(captor.getValue().getStatus()).isEqualTo(MentorStatus.APPROVED);
        }

        @Test
        @DisplayName("PROFICIENT 등급 - 항상 PENDING, novelRepository 조회 안 함")
        void register_proficient_always_pending() throws Exception {
            MentorRegisterRequest proficientRequest = new MentorRegisterRequest(
                    "판타지 장르를 10년째 쓰고 있습니다",
                    List.of("판타지"),
                    List.of("문장력", "플롯"),
                    CareerLevel.PROFICIENT,
                    "수상 경력", null, 3, true, null
            );

            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.PENDING)).willReturn(false);
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.APPROVED)).willReturn(false);
            given(objectMapper.writeValueAsString(any())).willReturn("[\"판타지\"]");

            ArgumentCaptor<Mentor> captor = ArgumentCaptor.forClass(Mentor.class);
            given(mentorRepository.save(captor.capture())).willReturn(mentor);

            mentorService.register(USER_ID, proficientRequest);

            assertThat(captor.getValue().getStatus()).isEqualTo(MentorStatus.PENDING);
            verify(novelRepository, never()).findNovelIdsByAuthorId(any());
        }

        @Test
        @DisplayName("소설이 없으면 PENDING으로 저장, episodeRepository 조회 안 함")
        void register_no_novels_pending() throws Exception {
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.PENDING)).willReturn(false);
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.APPROVED)).willReturn(false);
            given(novelRepository.findNovelIdsByAuthorId(USER_ID)).willReturn(List.of());
            given(objectMapper.writeValueAsString(any())).willReturn("[\"판타지\"]");

            ArgumentCaptor<Mentor> captor = ArgumentCaptor.forClass(Mentor.class);
            given(mentorRepository.save(captor.capture())).willReturn(mentor);

            mentorService.register(USER_ID, registerRequest);

            assertThat(captor.getValue().getStatus()).isEqualTo(MentorStatus.PENDING);
            verify(episodeRepository, never()).countByNovelIdInAndStatus(any(), any());
        }

        @Test
        @DisplayName("이미 PENDING 상태인 경우 예외 발생")
        void register_pending_exists_throws() {
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.PENDING)).willReturn(true);

            assertThatThrownBy(() -> mentorService.register(USER_ID, registerRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_PENDING_EXISTS.getMessage());

            verify(mentorRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 APPROVED 상태인 경우 예외 발생")
        void register_approved_exists_throws() {
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.PENDING)).willReturn(false);
            given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.APPROVED)).willReturn(true);

            assertThatThrownBy(() -> mentorService.register(USER_ID, registerRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_ALREADY_APPROVED.getMessage());

            verify(mentorRepository, never()).save(any());
        }
    }

    // ===================== update 테스트 =====================

    @Nested
    @DisplayName("멘토 정보 수정")
    class UpdateTest {

        @Test
        @DisplayName("정상적으로 멘토 정보 수정")
        void update_success() throws Exception {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(objectMapper.writeValueAsString(any())).willReturn("[\"판타지\"]");

            MentorUpdateResponse response = mentorService.update(USER_ID, updateRequest);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void update_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorService.update(USER_ID, updateRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("careerHistory가 빈 문자열이면 예외 발생")
        void update_blank_career_history_throws() {
            MentorUpdateRequest blankCareerRequest = new MentorUpdateRequest(
                    "수정된 소개글입니다", null, null, "", null, null, null, null
            );
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));

            assertThatThrownBy(() -> mentorService.update(USER_ID, blankCareerRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_CAREER_REQUIRED.getMessage());
        }

        @Test
        @DisplayName("careerHistory가 null이면 기존 값 유지")
        void update_null_career_history_keeps_existing() throws Exception {
            MentorUpdateRequest nullCareerRequest = new MentorUpdateRequest(
                    null, null, null, null, null, null, null, null
            );
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));

            MentorUpdateResponse response = mentorService.update(USER_ID, nullCareerRequest);

            assertThat(response).isNotNull();
        }
    }

    // ===================== getMyProfile 테스트 =====================

    @Nested
    @DisplayName("내 멘토 프로필 조회")
    class GetMyProfileTest {

        @Test
        @DisplayName("정상적으로 프로필 조회")
        void getMyProfile_success() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));

            MentorProfileResponse response = mentorService.getMyProfile(USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MentorStatus.PENDING);
            assertThat(response.careerLevel()).isEqualTo(CareerLevel.INTRODUCTION);
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void getMyProfile_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorService.getMyProfile(USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }

    @Test
    @DisplayName("동시 요청으로 DataIntegrityViolationException 발생 시 MENTOR_ALREADY_APPROVED 예외로 변환")
    void register_data_integrity_violation_throws() throws Exception {
        given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.PENDING)).willReturn(false);
        given(mentorRepository.existsByUserIdAndStatus(USER_ID, MentorStatus.APPROVED)).willReturn(false);
        given(novelRepository.findNovelIdsByAuthorId(USER_ID)).willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[\"판타지\"]");
        given(mentorRepository.save(any())).willThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> mentorService.register(USER_ID, registerRequest))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(MentorExceptionEnum.MENTOR_ALREADY_APPROVED.getMessage());
    }

    // ===================== getMyStatus 테스트 =====================

    @Nested
    @DisplayName("멘토 등록 상태 조회")
    class GetMyStatusTest {

        @Test
        @DisplayName("PENDING 상태 정상 조회")
        void getMyStatus_pending_success() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));

            MentorStatusResponse response = mentorService.getMyStatus(USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MentorStatus.PENDING);
            assertThat(response.rejectReason()).isNull();
        }

        @Test
        @DisplayName("APPROVED 상태 정상 조회")
        void getMyStatus_approved_success() {
            Mentor approvedMentor = mentor = Mentor.create(
                    USER_ID,
                    CareerLevel.INTRODUCTION,
                    "[\"판타지\"]",
                    "[\"문장력\"]",
                    "[\"꼼꼼한 피드백형\"]",
                    "판타지 장르를 10년째 쓰고 있습니다",
                    "2022 웹소설 신인상 수상",
                    3,
                    true,
                    "연재 의지가 강한 분을 환영합니다",
                    MentorStatus.APPROVED
            );
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(approvedMentor));

            MentorStatusResponse response = mentorService.getMyStatus(USER_ID);

            assertThat(response.status()).isEqualTo(MentorStatus.APPROVED);
            assertThat(response.rejectReason()).isNull();
        }

        @Test
        @DisplayName("REJECTED 상태 조회 시 rejectReason 반환")
        void getMyStatus_rejected_with_reason() {
            Mentor rejectedMentor = mentor = Mentor.create(
                    USER_ID,
                    CareerLevel.INTRODUCTION,
                    "[\"판타지\"]",
                    "[\"문장력\"]",
                    "[\"꼼꼼한 피드백형\"]",
                    "판타지 장르를 10년째 쓰고 있습니다",
                    "2022 웹소설 신인상 수상",
                    3,
                    true,
                    "연재 의지가 강한 분을 환영합니다",
                    MentorStatus.PENDING
            );
            rejectedMentor.reject("전문성 기준 미달입니다");
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(rejectedMentor));

            MentorStatusResponse response = mentorService.getMyStatus(USER_ID);

            assertThat(response.status()).isEqualTo(MentorStatus.REJECTED);
            assertThat(response.rejectReason()).isEqualTo("전문성 기준 미달입니다");
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void getMyStatus_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorService.getMyStatus(USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }

    // ===================== getStatistics 테스트 =====================

    @Nested
    @DisplayName("멘토링 통계 조회")
    class GetStatisticsTest {

        private static final Long MENTOR_ID = 10L;

        @BeforeEach
        void setMentorId() {
            try {
                java.lang.reflect.Field idField = Mentor.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(mentor, MENTOR_ID);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("정상적으로 통계 조회")
        void getStatistics_success() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.countByMentorIdAndStatus(MENTOR_ID, MentorshipStatus.PENDING))
                    .willReturn(3L);
            given(mentorshipRepository.countAcceptedThisMonth(eq(MENTOR_ID), any(LocalDateTime.class)))
                    .willReturn(1L);
            given(mentorshipRepository.countRejectedThisMonth(eq(MENTOR_ID), any(LocalDateTime.class)))
                    .willReturn(2L);

            MentorStatisticsResponse response = mentorService.getStatistics(USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.pendingCount()).isEqualTo(3L);
            assertThat(response.thisMonthAcceptedCount()).isEqualTo(1L);
            assertThat(response.thisMonthRejectedCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("통계가 모두 0인 경우 정상 조회")
        void getStatistics_all_zero() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.countByMentorIdAndStatus(any(), any())).willReturn(0L);
            given(mentorshipRepository.countAcceptedThisMonth(any(), any())).willReturn(0L);
            given(mentorshipRepository.countRejectedThisMonth(any(), any())).willReturn(0L);

            MentorStatisticsResponse response = mentorService.getStatistics(USER_ID);

            assertThat(response.pendingCount()).isEqualTo(0L);
            assertThat(response.thisMonthAcceptedCount()).isEqualTo(0L);
            assertThat(response.thisMonthRejectedCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void getStatistics_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorService.getStatistics(USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }

    // ===================== getMyMentees 테스트 =====================

    @Nested
    @DisplayName("내 멘티 목록 조회")
    class GetMyMenteesTest {

        private static final Long MENTOR_ENTITY_ID = 10L;
        private Mentorship mentorship;
        private User mentee;
        private Novel novel;

        @BeforeEach
        void setUp() {
            // title 제거 — Mentorship.create() 시그니처 변경 반영
            mentorship = Mentorship.create(MENTOR_ENTITY_ID, 501L, 100L,
                    "신청 동기", "https://s3.amazonaws.com/file.pdf");
            mentorship.approve();

            try {
                java.lang.reflect.Field idField = Mentorship.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(mentorship, 10L);

                java.lang.reflect.Field mentorIdField = Mentor.class.getDeclaredField("id");
                mentorIdField.setAccessible(true);
                mentorIdField.set(mentor, MENTOR_ENTITY_ID);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            mentee = User.builder()
                    .email("mentee@test.com")
                    .password("password")
                    .nickname("홍길동")
                    .role(UserRole.AUTHOR)
                    .build();

            novel = Novel.createNovel(USER_ID, "자바 백엔드 로드맵", "소설 설명", "판타지", "태그");
        }

        @Test
        @DisplayName("정상 조회 - 멘티 목록 반환")
        void getMyMentees_success() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findAllByMentorIdAndStatus(MENTOR_ENTITY_ID, MentorshipStatus.ACCEPTED))
                    .willReturn(List.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(501L)).willReturn(Optional.of(mentee));
            given(novelRepository.findById(100L)).willReturn(Optional.of(novel));
            given(mentorFeedbackRepository.findTopByMentorshipIdOrderByCreatedAtDesc(10L))
                    .willReturn(Optional.empty());

            List<MenteeInfoResponse> result = mentorService.getMyMentees(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).menteeName()).isEqualTo("홍길동");
            assertThat(result.get(0).novelTitle()).isEqualTo("자바 백엔드 로드맵");
            assertThat(result.get(0).lastFeedbackAt()).isNull();
            assertThat(result.get(0).status()).isEqualTo(MentorshipStatus.ACCEPTED);
        }

        @Test
        @DisplayName("최근 피드백이 있는 경우 lastFeedbackAt 반환")
        void getMyMentees_with_last_feedback() {
            // MentorFeedback.create() 시그니처 변경 반영 — title, sessionNumber 추가
            MentorFeedback feedback = MentorFeedback.create(
                    10L, USER_ID, "1회차 피드백", 1, "피드백 내용"
            );

            try {
                java.lang.reflect.Field createdAtField = feedback.getClass().getSuperclass().getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(feedback, LocalDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findAllByMentorIdAndStatus(MENTOR_ENTITY_ID, MentorshipStatus.ACCEPTED))
                    .willReturn(List.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(501L)).willReturn(Optional.of(mentee));
            given(novelRepository.findById(100L)).willReturn(Optional.of(novel));
            given(mentorFeedbackRepository.findTopByMentorshipIdOrderByCreatedAtDesc(10L))
                    .willReturn(Optional.of(feedback));

            List<MenteeInfoResponse> result = mentorService.getMyMentees(USER_ID);

            assertThat(result.get(0).lastFeedbackAt()).isNotNull();
        }

        @Test
        @DisplayName("멘티가 탈퇴한 경우 알 수 없는 사용자 반환")
        void getMyMentees_deleted_mentee() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findAllByMentorIdAndStatus(MENTOR_ENTITY_ID, MentorshipStatus.ACCEPTED))
                    .willReturn(List.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(501L)).willReturn(Optional.empty());
            given(novelRepository.findById(100L)).willReturn(Optional.of(novel));
            given(mentorFeedbackRepository.findTopByMentorshipIdOrderByCreatedAtDesc(10L))
                    .willReturn(Optional.empty());

            List<MenteeInfoResponse> result = mentorService.getMyMentees(USER_ID);

            assertThat(result.get(0).menteeName()).isEqualTo("알 수 없는 사용자");
        }

        @Test
        @DisplayName("소설이 없는 경우 알 수 없는 소설 반환")
        void getMyMentees_deleted_novel() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findAllByMentorIdAndStatus(MENTOR_ENTITY_ID, MentorshipStatus.ACCEPTED))
                    .willReturn(List.of(mentorship));
            given(userRepository.findByIdAndIsDeletedFalse(501L)).willReturn(Optional.of(mentee));
            given(novelRepository.findById(100L)).willReturn(Optional.empty());
            given(mentorFeedbackRepository.findTopByMentorshipIdOrderByCreatedAtDesc(10L))
                    .willReturn(Optional.empty());

            List<MenteeInfoResponse> result = mentorService.getMyMentees(USER_ID);

            assertThat(result.get(0).novelTitle()).isEqualTo("알 수 없는 소설");
        }

        @Test
        @DisplayName("멘티가 없는 경우 빈 리스트 반환")
        void getMyMentees_empty() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.findAllByMentorIdAndStatus(MENTOR_ENTITY_ID, MentorshipStatus.ACCEPTED))
                    .willReturn(List.of());

            List<MenteeInfoResponse> result = mentorService.getMyMentees(USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void getMyMentees_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorService.getMyMentees(USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }

    // ===================== getStatisticsDetail 테스트 =====================

    @Nested
    @DisplayName("멘토링 통계 상세 조회")
    class GetStatisticsDetailTest {

        private static final Long MENTOR_ENTITY_ID = 10L;

        @BeforeEach
        void setMentorId() {
            try {
                java.lang.reflect.Field idField = Mentor.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(mentor, MENTOR_ENTITY_ID);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("정상적으로 통계 상세 조회")
        void getStatisticsDetail_success() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipReviewRepository.countTotalMenteesByMentorId(MENTOR_ENTITY_ID)).willReturn(12L);
            given(mentorshipReviewRepository.countCompletedSessionsByMentorId(MENTOR_ENTITY_ID)).willReturn(34L);
            given(mentorshipReviewRepository.findAverageRatingByMentorId(MENTOR_ENTITY_ID)).willReturn(4.9);

            MentorStatisticsDetailResponse response = mentorService.getStatisticsDetail(USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.totalMentees()).isEqualTo(12L);
            assertThat(response.completedSessions()).isEqualTo(34L);
            assertThat(response.averageSatisfaction()).isEqualTo(4.9);
        }

        @Test
        @DisplayName("리뷰가 없는 경우 만족도 0.0 반환")
        void getStatisticsDetail_no_reviews() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipReviewRepository.countTotalMenteesByMentorId(MENTOR_ENTITY_ID)).willReturn(0L);
            given(mentorshipReviewRepository.countCompletedSessionsByMentorId(MENTOR_ENTITY_ID)).willReturn(0L);
            given(mentorshipReviewRepository.findAverageRatingByMentorId(MENTOR_ENTITY_ID)).willReturn(null);

            MentorStatisticsDetailResponse response = mentorService.getStatisticsDetail(USER_ID);

            assertThat(response.totalMentees()).isEqualTo(0L);
            assertThat(response.completedSessions()).isEqualTo(0L);
            assertThat(response.averageSatisfaction()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("만족도 소수점 첫째 자리 반올림 확인")
        void getStatisticsDetail_rating_rounded() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipReviewRepository.countTotalMenteesByMentorId(MENTOR_ENTITY_ID)).willReturn(5L);
            given(mentorshipReviewRepository.countCompletedSessionsByMentorId(MENTOR_ENTITY_ID)).willReturn(10L);
            given(mentorshipReviewRepository.findAverageRatingByMentorId(MENTOR_ENTITY_ID)).willReturn(4.85);

            MentorStatisticsDetailResponse response = mentorService.getStatisticsDetail(USER_ID);

            assertThat(response.averageSatisfaction()).isEqualTo(4.9);
        }

        @Test
        @DisplayName("멘토 프로필이 없으면 예외 발생")
        void getStatisticsDetail_mentor_not_found_throws() {
            given(mentorRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorService.getStatisticsDetail(USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }
}