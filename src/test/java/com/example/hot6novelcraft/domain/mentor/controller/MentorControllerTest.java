package com.example.hot6novelcraft.domain.mentor.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorRegisterRequest;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorUpdateRequest;
import com.example.hot6novelcraft.domain.mentor.dto.response.*;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.service.MentorService;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MentorControllerTest {

    @InjectMocks
    private MentorController mentorController;

    @Mock
    private MentorService mentorService;

    private UserDetailsImpl userDetails;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .email("test@test.com")
                .password("password")
                .nickname("테스트유저")
                .role(UserRole.AUTHOR)
                .build();

        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, USER_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        userDetails = new UserDetailsImpl(user, null);
    }

    // ===================== register 테스트 =====================

    @Nested
    @DisplayName("POST /api/mentors - 멘토 등록 신청")
    class RegisterTest {

        @Test
        @DisplayName("정상 등록 시 201 반환")
        void register_success_returns_201() {
            MentorRegisterRequest request = new MentorRegisterRequest(
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

            MentorRegisterResponse mockResponse = new MentorRegisterResponse(
                    1L, MentorStatus.PENDING, LocalDateTime.now()
            );

            given(mentorService.register(eq(USER_ID), any())).willReturn(mockResponse);
            ResponseEntity<BaseResponse<MentorRegisterResponse>> response =
                    mentorController.register(userDetails, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("201");
            assertThat(response.getBody().data().status()).isEqualTo(MentorStatus.PENDING);
            verify(mentorService, times(1)).register(eq(USER_ID), any());
        }

        @Test
        @DisplayName("자동 승인된 경우 APPROVED 상태 반환")
        void register_approved_returns_approved_status() {
            MentorRegisterRequest request = new MentorRegisterRequest(
                    "판타지 장르를 10년째 쓰고 있습니다",
                    List.of("판타지"),
                    List.of("문장력", "플롯"),
                    CareerLevel.INTRODUCTION,
                    null, null, 3, true, null
            );

            MentorRegisterResponse mockResponse = new MentorRegisterResponse(
                    1L, MentorStatus.APPROVED, LocalDateTime.now()
            );

            given(mentorService.register(eq(USER_ID), any())).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorRegisterResponse>> response =
                    mentorController.register(userDetails, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().status()).isEqualTo(MentorStatus.APPROVED);
        }
    }

    // ===================== update 테스트 =====================

    @Nested
    @DisplayName("PUT /api/mentors/me - 멘토 정보 수정")
    class UpdateTest {

        @Test
        @DisplayName("정상 수정 시 200 반환")
        void update_success_returns_200() {
            MentorUpdateRequest request = new MentorUpdateRequest(
                    "수정된 소개글입니다. 판타지 장르 10년차입니다",
                    List.of("판타지"),
                    List.of("문장력"),
                    "2024년 공모전 대상 수상",
                    List.of("방향 제시형"),
                    5,
                    false,
                    "성실한 분 환영합니다"
            );

            MentorUpdateResponse mockResponse = new MentorUpdateResponse(1L, LocalDateTime.now());

            given(mentorService.update(eq(USER_ID), any())).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorUpdateResponse>> response =
                    mentorController.update(userDetails, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data().mentorId()).isEqualTo(1L);
            verify(mentorService, times(1)).update(eq(USER_ID), any());
        }

        @Test
        @DisplayName("일부 필드만 수정해도 200 반환")
        void update_partial_fields_returns_200() {
            MentorUpdateRequest partialRequest = new MentorUpdateRequest(
                    null, null, null,
                    "경력 추가",
                    null, null, null, null
            );

            MentorUpdateResponse mockResponse = new MentorUpdateResponse(1L, LocalDateTime.now());
            given(mentorService.update(eq(USER_ID), any())).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorUpdateResponse>> response =
                    mentorController.update(userDetails, partialRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===================== getMyProfile 테스트 =====================

    @Nested
    @DisplayName("GET /api/mentors/me - 내 멘토 프로필 조회")
    class GetMyProfileTest {

        @Test
        @DisplayName("정상 조회 시 200 반환")
        void getMyProfile_success_returns_200() {
            MentorProfileResponse mockResponse = new MentorProfileResponse(
                    1L,
                    "판타지 장르를 10년째 쓰고 있습니다",
                    List.of("판타지"),
                    List.of("문장력"),
                    CareerLevel.INTRODUCTION,
                    "2022 웹소설 신인상 수상",
                    List.of("꼼꼼한 피드백형"),
                    3,
                    true,
                    "연재 의지가 강한 분을 환영합니다",
                    MentorStatus.APPROVED
            );

            given(mentorService.getMyProfile(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorProfileResponse>> response =
                    mentorController.getMyProfile(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data().mentorId()).isEqualTo(1L);
            assertThat(response.getBody().data().status()).isEqualTo(MentorStatus.APPROVED);
            verify(mentorService, times(1)).getMyProfile(USER_ID);
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void getMyProfile_message_check() {
            MentorProfileResponse mockResponse = new MentorProfileResponse(
                    1L, "소개글", List.of(), List.of(),
                    CareerLevel.INTRODUCTION, null, List.of(),
                    3, true, null, MentorStatus.PENDING
            );

            given(mentorService.getMyProfile(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorProfileResponse>> response =
                    mentorController.getMyProfile(userDetails);

            assertThat(response.getBody().message()).isEqualTo("내 멘토 프로필 조회 성공");
        }
    }

    // ===================== getMyStatus 테스트 =====================

    @Nested
    @DisplayName("GET /api/mentors/me/status - 멘토 등록 상태 조회")
    class GetMyStatusTest {

        @Test
        @DisplayName("PENDING 상태 조회 시 200 반환")
        void getMyStatus_pending_returns_200() {
            MentorStatusResponse mockResponse = new MentorStatusResponse(
                    1L, MentorStatus.PENDING, null,null,null
            );
            given(mentorService.getMyStatus(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatusResponse>> response =
                    mentorController.getMyStatus(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data().status()).isEqualTo(MentorStatus.PENDING);
            assertThat(response.getBody().data().rejectReason()).isNull();
            verify(mentorService, times(1)).getMyStatus(USER_ID);
        }

        @Test
        @DisplayName("APPROVED 상태 조회 시 rejectReason null 반환")
        void getMyStatus_approved_returns_200() {
            MentorStatusResponse mockResponse = new MentorStatusResponse(
                    1L, MentorStatus.APPROVED, null,null,null
            );
            given(mentorService.getMyStatus(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatusResponse>> response =
                    mentorController.getMyStatus(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo(MentorStatus.APPROVED);
            assertThat(response.getBody().data().rejectReason()).isNull();
        }

        @Test
        @DisplayName("REJECTED 상태 조회 시 rejectReason 반환")
        void getMyStatus_rejected_returns_rejectReason() {
            MentorStatusResponse mockResponse = new MentorStatusResponse(
                    1L, MentorStatus.REJECTED, "전문성 기준 미달입니다",null,null
            );
            given(mentorService.getMyStatus(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatusResponse>> response =
                    mentorController.getMyStatus(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo(MentorStatus.REJECTED);
            assertThat(response.getBody().data().rejectReason()).isEqualTo("전문성 기준 미달입니다");
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void getMyStatus_message_check() {
            MentorStatusResponse mockResponse = new MentorStatusResponse(
                    1L, MentorStatus.PENDING, null,null,null
            );
            given(mentorService.getMyStatus(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatusResponse>> response =
                    mentorController.getMyStatus(userDetails);

            assertThat(response.getBody().message()).isEqualTo("멘토 등록 상태 조회가 완료되었습니다");
        }
    }

    // ===================== getStatistics 테스트 =====================

    @Nested
    @DisplayName("GET /api/mentors/me/statistics - 멘토링 통계 조회")
    class GetStatisticsTest {

        @Test
        @DisplayName("정상 조회 시 200 반환")
        void getStatistics_success_returns_200() {
            MentorStatisticsResponse mockResponse = new MentorStatisticsResponse(3L, 1L, 2L);
            given(mentorService.getStatistics(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatisticsResponse>> response =
                    mentorController.getStatistics(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data().pendingCount()).isEqualTo(3L);
            assertThat(response.getBody().data().thisMonthAcceptedCount()).isEqualTo(1L);
            assertThat(response.getBody().data().thisMonthRejectedCount()).isEqualTo(2L);
            verify(mentorService, times(1)).getStatistics(USER_ID);
        }

        @Test
        @DisplayName("통계가 모두 0인 경우 200 반환")
        void getStatistics_all_zero_returns_200() {
            MentorStatisticsResponse mockResponse = new MentorStatisticsResponse(0L, 0L, 0L);
            given(mentorService.getStatistics(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatisticsResponse>> response =
                    mentorController.getStatistics(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().pendingCount()).isEqualTo(0L);
            assertThat(response.getBody().data().thisMonthAcceptedCount()).isEqualTo(0L);
            assertThat(response.getBody().data().thisMonthRejectedCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void getStatistics_message_check() {
            MentorStatisticsResponse mockResponse = new MentorStatisticsResponse(0L, 0L, 0L);
            given(mentorService.getStatistics(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatisticsResponse>> response =
                    mentorController.getStatistics(userDetails);

            assertThat(response.getBody().message()).isEqualTo("멘토링 통계 조회가 완료되었습니다");
        }
    }

    // ===================== getMyMentees 테스트 =====================

    @Nested
    @DisplayName("GET /api/mentors/me/mentees - 내 멘티 목록 조회")
    class GetMyMenteesTest {

        @Test
        @DisplayName("정상 조회 시 200 반환")
        void getMyMentees_success_returns_200() {
            MenteeInfoResponse mockMentee = new MenteeInfoResponse(
                    10L, 501L, "홍길동", "자바 백엔드 로드맵",
                    5, 2, MentorshipStatus.ACCEPTED,
                    LocalDateTime.now(), LocalDateTime.now().minusDays(1)
            );
            given(mentorService.getMyMentees(USER_ID)).willReturn(List.of(mockMentee));

            ResponseEntity<BaseResponse<List<MenteeInfoResponse>>> response =
                    mentorController.getMyMentees(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data()).hasSize(1);
            assertThat(response.getBody().data().get(0).menteeName()).isEqualTo("홍길동");
            assertThat(response.getBody().data().get(0).totalSessions()).isEqualTo(5);
            assertThat(response.getBody().data().get(0).manuscriptDownloadCount()).isEqualTo(2);
            verify(mentorService, times(1)).getMyMentees(USER_ID);
        }

        @Test
        @DisplayName("멘티가 없는 경우 빈 리스트 반환")
        void getMyMentees_empty_returns_200() {
            given(mentorService.getMyMentees(USER_ID)).willReturn(List.of());

            ResponseEntity<BaseResponse<List<MenteeInfoResponse>>> response =
                    mentorController.getMyMentees(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).isEmpty();
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void getMyMentees_message_check() {
            given(mentorService.getMyMentees(USER_ID)).willReturn(List.of());

            ResponseEntity<BaseResponse<List<MenteeInfoResponse>>> response =
                    mentorController.getMyMentees(userDetails);

            assertThat(response.getBody().message()).isEqualTo("내 멘티 목록 조회가 완료되었습니다");
        }
    }

    // ===================== getStatisticsDetail 테스트 =====================

    @Nested
    @DisplayName("GET /api/mentors/me/statistics/detail - 멘토링 통계 상세 조회")
    class GetStatisticsDetailTest {

        @Test
        @DisplayName("정상 조회 시 200 반환")
        void getStatisticsDetail_success_returns_200() {
            MentorStatisticsDetailResponse mockResponse = new MentorStatisticsDetailResponse(
                    12L, 34L, 4.9
            );
            given(mentorService.getStatisticsDetail(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatisticsDetailResponse>> response =
                    mentorController.getStatisticsDetail(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data().totalMentees()).isEqualTo(12L);
            assertThat(response.getBody().data().completedSessions()).isEqualTo(34L);
            assertThat(response.getBody().data().averageSatisfaction()).isEqualTo(4.9);
            verify(mentorService, times(1)).getStatisticsDetail(USER_ID);
        }

        @Test
        @DisplayName("리뷰가 없는 경우 만족도 0.0 반환")
        void getStatisticsDetail_no_reviews_returns_zero() {
            MentorStatisticsDetailResponse mockResponse = new MentorStatisticsDetailResponse(
                    0L, 0L, 0.0
            );
            given(mentorService.getStatisticsDetail(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatisticsDetailResponse>> response =
                    mentorController.getStatisticsDetail(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalMentees()).isEqualTo(0L);
            assertThat(response.getBody().data().completedSessions()).isEqualTo(0L);
            assertThat(response.getBody().data().averageSatisfaction()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void getStatisticsDetail_message_check() {
            MentorStatisticsDetailResponse mockResponse = new MentorStatisticsDetailResponse(0L, 0L, 0.0);
            given(mentorService.getStatisticsDetail(USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentorStatisticsDetailResponse>> response =
                    mentorController.getStatisticsDetail(userDetails);

            assertThat(response.getBody().message()).isEqualTo("멘토링 통계 상세 조회가 완료되었습니다");
        }
    }
}