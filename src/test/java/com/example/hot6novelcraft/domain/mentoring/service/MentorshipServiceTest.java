package com.example.hot6novelcraft.domain.mentoring.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.MentoringExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.domain.file.service.FileUploadService;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentorshipCreateRequest;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorshipDetailResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorshipHistoryResponse;
import com.example.hot6novelcraft.domain.mentoring.entity.Mentorship;
import com.example.hot6novelcraft.domain.mentoring.repository.MentorshipRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorWithNickname;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorshipListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MentorshipServiceTest {

    @InjectMocks
    private MentorshipService mentorshipService;

    @Mock
    private MentorshipRepository mentorshipRepository;
    @Mock
    private MentorRepository mentorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileUploadService fileUploadService;

    // 상수
    private static final Long MENTEE_ID = 1L;
    private static final Long MENTOR_USER_ID = 2L;
    private static final Long MENTOR_ENTITY_ID = 5L;
    private static final Long NOVEL_ID = 100L;

    // 픽스처
    private User menteeUser;
    private Mentor mentor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mentorshipService, "objectMapper", new ObjectMapper());
        menteeUser = User.builder()
                .email("mentee@test.com")
                .password("pw")
                .nickname("홍길동")
                .role(UserRole.AUTHOR)
                .build();

        mentor = Mentor.create(
                MENTOR_USER_ID, CareerLevel.INTRODUCTION,
                "[\"판타지\"]", "[\"문장력\"]", "[\"꼼꼼한 피드백형\"]",
                "소개글", "수상경력", 3, true, "멘티 설명", MentorStatus.APPROVED
        );
        setField(mentor, "id", MENTOR_ENTITY_ID);
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

    // ==================== 멘토링 신청 ====================
    @Nested
    @DisplayName("멘토링 신청")
    class ApplyMentorshipTest {

        @Test
        @DisplayName("정상 신청 - 저장 호출")
        void applyMentorship_success() {
            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "신청 동기입니다.", NOVEL_ID, "https://s3.amazonaws.com/test.txt"
            );

            // save() 반환값 mock 추가!
            Mentorship savedMentorship = Mentorship.create(
                    MENTOR_USER_ID, MENTEE_ID, NOVEL_ID, "신청 동기입니다.",
                    "https://s3.amazonaws.com/test.txt"
            );
            setField(savedMentorship, "id", 10L);

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTOR_USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.existsByMenteeIdAndStatusIn(eq(MENTEE_ID), any()))
                    .willReturn(false);
            given(mentorshipRepository.save(any(Mentorship.class))).willReturn(savedMentorship);  // ← 추가!

            mentorshipService.applyMentorship(MENTEE_ID, request);

            verify(mentorshipRepository).save(any(Mentorship.class));
        }

        @Test
        @DisplayName("작가 권한 없으면 예외")
        void applyMentorship_not_author_throws() {
            User reader = User.builder()
                    .email("reader@test.com")
                    .password("pw")
                    .nickname("독자")
                    .role(UserRole.READER)
                    .build();

            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(reader));

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_AUTHOR.getMessage());
        }

        @Test
        @DisplayName("본인에게 신청 시 예외")
        void applyMentorship_self_apply_throws() {
            // mentor의 userId를 menteeId와 같게 설정
            Mentor selfMentor = mentor = Mentor.create(
                    MENTEE_ID, CareerLevel.INTRODUCTION,
                    "[\"판타지\"]", "[\"문장력\"]", "[\"꼼꼼한 피드백형\"]",
                    "소개글", "수상경력", 3, true, "멘티 설명", MentorStatus.APPROVED
            );
            setField(selfMentor, "id", MENTOR_ENTITY_ID);

            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTEE_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTEE_ID)).willReturn(Optional.of(selfMentor));

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_SELF_APPLY.getMessage());
        }

        @Test
        @DisplayName("이미 진행중인 멘토링 있으면 예외")
        void applyMentorship_already_exists_throws() {
            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTOR_USER_ID)).willReturn(Optional.of(mentor));
            given(mentorshipRepository.existsByMenteeIdAndStatusIn(eq(MENTEE_ID), any()))
                    .willReturn(true);

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_ALREADY_EXISTS.getMessage());
        }

        @Test
        @DisplayName("정원 마감이면 예외")
        void applyMentorship_slot_full_throws() {
            Mentor fullMentor = mentor = Mentor.create(
                    MENTOR_USER_ID, CareerLevel.INTRODUCTION,
                    "[\"판타지\"]", "[\"문장력\"]", "[\"꼼꼼한 피드백형\"]",
                    "소개글", "수상경력", 0, true, "멘티 설명", MentorStatus.APPROVED
            );
            setField(fullMentor, "id", MENTOR_ENTITY_ID);

            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTOR_USER_ID)).willReturn(Optional.of(fullMentor));
            given(mentorshipRepository.existsByMenteeIdAndStatusIn(eq(MENTEE_ID), any()))
                    .willReturn(false);

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_SLOT_FULL.getMessage());
        }

        @Test
        @DisplayName("멘토를 찾을 수 없으면 예외")
        void applyMentorship_mentor_not_found_throws() {
            MentorshipCreateRequest request = new MentorshipCreateRequest(
                    MENTOR_USER_ID, "동기", NOVEL_ID, null
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorRepository.findByUserId(MENTOR_USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorshipService.applyMentorship(MENTEE_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }

    // ==================== 원고 업로드 ====================
    @Nested
    @DisplayName("원고 업로드")
    class UploadManuscriptTest {

        @Test
        @DisplayName("정상 업로드 - S3 URL 반환")
        void uploadManuscript_success() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "content".getBytes()
            );
            String expectedUrl = "https://bucket.s3.amazonaws.com/manuscripts/uuid.txt";

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(fileUploadService.uploadManuscript(file)).willReturn(expectedUrl);

            String result = mentorshipService.uploadManuscript(file, MENTEE_ID);

            assertThat(result).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("작가 권한 없으면 예외")
        void uploadManuscript_not_author_throws() {
            User reader = User.builder()
                    .email("reader@test.com")
                    .password("pw")
                    .nickname("독자")
                    .role(UserRole.READER)
                    .build();

            MultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "content".getBytes()
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(reader));

            assertThatThrownBy(() -> mentorshipService.uploadManuscript(file, MENTEE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_AUTHOR.getMessage());
        }

        @Test
        @DisplayName("유저 없으면 예외")
        void uploadManuscript_user_not_found_throws() {
            MultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "content".getBytes()
            );

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorshipService.uploadManuscript(file, MENTEE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_NOT_FOUND_USER.getMessage());
        }
    }

    // ==================== 멘토 목록 조회 ====================
    @Nested
    @DisplayName("멘토 목록 조회")
    class GetMentorListTest {

        @Test
        @DisplayName("전체 목록 조회 성공")
        void getMentorList_success() {
            Pageable pageable = PageRequest.of(0, 20);

            MentorWithNickname mentor1 = new MentorWithNickname(
                    1L, "멘토1", CareerLevel.PROFICIENT,
                    "[\"판타지\",\"로판\"]", "[\"문장력\"]", "[\"꼼꼼한 피드백형\"]",
                    "수상 3회", 5
            );
            MentorWithNickname mentor2 = new MentorWithNickname(
                    2L, "멘토2", CareerLevel.INTERMEDIATE,
                    "[\"SF\"]", "[\"세계관\"]", "[\"따뜻한 피드백형\"]",
                    "출간 경력", 3
            );

            Page<MentorWithNickname> page = new PageImpl<>(List.of(mentor1, mentor2), pageable, 2);
            given(mentorshipRepository.findMentorList(null, null,null, pageable)).willReturn(page);

            Page<MentorshipListResponse> result = mentorshipService.getMentorList(null, null, null,pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).nickname()).isEqualTo("멘토1");
            assertThat(result.getContent().get(0).mainGenres()).containsExactly("판타지", "로판");
            assertThat(result.getContent().get(1).mentoringStyle()).containsExactly("따뜻한 피드백형");
        }

        @Test
        @DisplayName("장르 필터 조회")
        void getMentorList_filter_by_genre() {
            Pageable pageable = PageRequest.of(0, 20);

            MentorWithNickname mentor = new MentorWithNickname(
                    1L, "멘토1", CareerLevel.PROFICIENT,
                    "[\"판타지\"]", "[\"문장력\"]", "[\"꼼꼼한 피드백형\"]",
                    "수상 3회", 5
            );

            Page<MentorWithNickname> page = new PageImpl<>(List.of(mentor), pageable, 1);
            given(mentorshipRepository.findMentorList("판타지", null,null, pageable)).willReturn(page);

            Page<MentorshipListResponse> result = mentorshipService.getMentorList("판타지", null, null,pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).mainGenres()).contains("판타지");
        }

        @Test
        @DisplayName("등급 필터 조회")
        void getMentorList_filter_by_careerLevel() {
            Pageable pageable = PageRequest.of(0, 20);

            MentorWithNickname mentor = new MentorWithNickname(
                    1L, "멘토1", CareerLevel.INTERMEDIATE,
                    "[\"SF\"]", "[\"세계관\"]", "[\"따뜻한 피드백형\"]",
                    "출간 경력", 3
            );

            Page<MentorWithNickname> page = new PageImpl<>(List.of(mentor), pageable, 1);
            given(mentorshipRepository.findMentorList(null, CareerLevel.INTERMEDIATE,null, pageable)).willReturn(page);

            Page<MentorshipListResponse> result = mentorshipService.getMentorList(null, CareerLevel.INTERMEDIATE, null,pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).careerLevel()).isEqualTo(CareerLevel.INTERMEDIATE);
        }

        @Test
        @DisplayName("결과 없으면 빈 페이지")
        void getMentorList_empty() {
            Pageable pageable = PageRequest.of(0, 20);

            Page<MentorWithNickname> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(mentorshipRepository.findMentorList("없는장르", null, null,pageable)).willReturn(emptyPage);

            Page<MentorshipListResponse> result = mentorshipService.getMentorList("없는장르", null, null, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ==================== 멘토 상세 조회 ====================
    @Nested
    @DisplayName("멘토 상세 조회")
    class GetMentorDetailTest {

        @Test
        @DisplayName("정상 조회 성공")
        void getMentorDetail_success() {
            given(mentorRepository.findById(MENTOR_ENTITY_ID)).willReturn(Optional.of(mentor));
            given(userRepository.findById(MENTOR_USER_ID)).willReturn(Optional.of(
                    User.builder().email("mentor@test.com").password("pw")
                            .nickname("멘토닉네임").role(UserRole.AUTHOR).build()
            ));

            MentorshipDetailResponse result = mentorshipService.getMentorDetail(MENTOR_ENTITY_ID);

            assertThat(result.mentorId()).isEqualTo(MENTOR_ENTITY_ID);
            assertThat(result.nickname()).isEqualTo("멘토닉네임");
            assertThat(result.bio()).isEqualTo("소개글");
        }

        @Test
        @DisplayName("멘토 못 찾으면 예외")
        void getMentorDetail_not_found() {
            given(mentorRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> mentorshipService.getMentorDetail(999L))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentorExceptionEnum.MENTOR_NOT_FOUND.getMessage());
        }
    }

    // ==================== 내 멘토링 이력 조회 ====================
    @Nested
    @DisplayName("내 멘토링 이력 조회")
    class GetMyHistoryTest {

        @Test
        @DisplayName("전체 이력 조회 성공")
        void getMyHistory_success() {
            Mentorship mentorship = Mentorship.create(
                    MENTOR_ENTITY_ID, MENTEE_ID, NOVEL_ID, "동기",
                    "https://s3.amazonaws.com/test.txt"
            );
            setField(mentorship, "id", 10L);

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorshipRepository.findAllByMenteeIdOrderByCreatedAtDesc(MENTEE_ID))
                    .willReturn(List.of(mentorship));
            given(mentorRepository.findById(MENTOR_ENTITY_ID)).willReturn(Optional.of(mentor));
            given(userRepository.findById(MENTOR_USER_ID)).willReturn(Optional.of(
                    User.builder().email("m@test.com").password("pw")
                            .nickname("멘토닉네임").role(UserRole.AUTHOR).build()
            ));

            List<MentorshipHistoryResponse> result = mentorshipService.getMyHistory(MENTEE_ID, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).mentorNickname()).isEqualTo("멘토닉네임");
        }

        @Test
        @DisplayName("작가 권한 없으면 예외")
        void getMyHistory_not_author_throws() {
            User reader = User.builder()
                    .email("reader@test.com").password("pw")
                    .nickname("독자").role(UserRole.READER).build();

            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(reader));

            assertThatThrownBy(() -> mentorshipService.getMyHistory(MENTEE_ID, null))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(MentoringExceptionEnum.MENTORING_NOT_AUTHOR.getMessage());
        }

        @Test
        @DisplayName("이력 없으면 빈 리스트")
        void getMyHistory_empty() {
            given(userRepository.findById(MENTEE_ID)).willReturn(Optional.of(menteeUser));
            given(mentorshipRepository.findAllByMenteeIdOrderByCreatedAtDesc(MENTEE_ID))
                    .willReturn(List.of());

            List<MentorshipHistoryResponse> result = mentorshipService.getMyHistory(MENTEE_ID, null);

            assertThat(result).isEmpty();
        }
    }
}