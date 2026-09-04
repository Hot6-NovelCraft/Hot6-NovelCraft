package com.example.hot6novelcraft.domain.mentoring.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentoringFeedbackRequest;
import com.example.hot6novelcraft.domain.mentoring.dto.response.ManuscriptUrlResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringDetailResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringFeedbackResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringReceivedResponse;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.mentoring.service.MentoringService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentoringControllerTest {

    @InjectMocks
    private MentoringController mentoringController;

    @Mock
    private MentoringService mentoringService;

    private UserDetailsImpl userDetails;
    private static final Long USER_ID = 1L;
    private static final Long MENTORING_ID = 10L;
    private static final Long MENTEE_ID = 501L;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .email("test@test.com")
                .password("password")
                .nickname("테스트멘토")
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

    @Nested
    @DisplayName("GET /api/mentorings/received - 내 멘토링 접수 목록 조회")
    class GetReceivedMentoringsTest {

        @Test
        @DisplayName("정상 조회 시 200 반환")
        void getReceivedMentorings_success_returns_200() {
            MentoringReceivedResponse item = new MentoringReceivedResponse(
                    MENTORING_ID, MENTEE_ID, "홍길동", "자바 백엔드 로드맵",
                    LocalDateTime.now(), MentorshipStatus.PENDING
            );
            PageImpl<MentoringReceivedResponse> page = new PageImpl<>(List.of(item));
            given(mentoringService.getReceivedMentorings(eq(USER_ID), any())).willReturn(page);

            ResponseEntity<BaseResponse<PageResponse<MentoringReceivedResponse>>> response =
                    mentoringController.getReceivedMentoringsV1(userDetails, 0, 10);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("COMMON-200");
            assertThat(response.getBody().data().content()).hasSize(1);
            assertThat(response.getBody().data().content().get(0).mentoringId()).isEqualTo(MENTORING_ID);
            verify(mentoringService, times(1)).getReceivedMentorings(eq(USER_ID), any());
        }

        @Test
        @DisplayName("빈 목록 조회 시 200 반환")
        void getReceivedMentorings_empty_returns_200() {
            PageImpl<MentoringReceivedResponse> emptyPage = new PageImpl<>(List.of());
            given(mentoringService.getReceivedMentorings(eq(USER_ID), any())).willReturn(emptyPage);

            ResponseEntity<BaseResponse<PageResponse<MentoringReceivedResponse>>> response =
                    mentoringController.getReceivedMentoringsV1(userDetails, 0, 10);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).isEmpty();
            assertThat(response.getBody().data().totalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void getReceivedMentorings_message_check() {
            PageImpl<MentoringReceivedResponse> emptyPage = new PageImpl<>(List.of());
            given(mentoringService.getReceivedMentorings(eq(USER_ID), any())).willReturn(emptyPage);

            ResponseEntity<BaseResponse<PageResponse<MentoringReceivedResponse>>> response =
                    mentoringController.getReceivedMentoringsV1(userDetails, 0, 10);

            assertThat(response.getBody().message()).isEqualTo("접수된 멘토링 목록 조회가 완료되었습니다");
        }
    }

    @Nested
    @DisplayName("PATCH /api/mentorings/{mentoringId}/mentees/{menteeId}/accept - 멘티 수락")
    class AcceptMenteeTest {

        @Test
        @DisplayName("정상 수락 시 200 반환")
        void acceptMentee_success_returns_200() {
            doNothing().when(mentoringService).acceptMentee(MENTORING_ID, MENTEE_ID, USER_ID);

            ResponseEntity<BaseResponse<Void>> response =
                    mentoringController.acceptMentee(MENTORING_ID, MENTEE_ID, userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data()).isNull();
            verify(mentoringService, times(1)).acceptMentee(MENTORING_ID, MENTEE_ID, USER_ID);
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void acceptMentee_message_check() {
            doNothing().when(mentoringService).acceptMentee(MENTORING_ID, MENTEE_ID, USER_ID);

            ResponseEntity<BaseResponse<Void>> response =
                    mentoringController.acceptMentee(MENTORING_ID, MENTEE_ID, userDetails);

            assertThat(response.getBody().message()).isEqualTo("멘티 수락이 완료되었습니다");
        }
    }

    @Nested
    @DisplayName("PATCH /api/mentorings/{mentoringId}/mentees/{menteeId}/reject - 멘티 거절")
    class RejectMenteeTest {

        @Test
        @DisplayName("정상 거절 시 200 반환")
        void rejectMentee_success_returns_200() {
            doNothing().when(mentoringService).rejectMentee(MENTORING_ID, MENTEE_ID, USER_ID);

            ResponseEntity<BaseResponse<Void>> response =
                    mentoringController.rejectMentee(MENTORING_ID, MENTEE_ID, userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data()).isNull();
            verify(mentoringService, times(1)).rejectMentee(MENTORING_ID, MENTEE_ID, USER_ID);
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void rejectMentee_message_check() {
            doNothing().when(mentoringService).rejectMentee(MENTORING_ID, MENTEE_ID, USER_ID);

            ResponseEntity<BaseResponse<Void>> response =
                    mentoringController.rejectMentee(MENTORING_ID, MENTEE_ID, userDetails);

            assertThat(response.getBody().message()).isEqualTo("멘티 거절이 완료되었습니다");
        }
    }

    @Nested
    @DisplayName("GET /api/mentorings/{mentoringId}/documents - 원고 다운로드")
    class GetManuscriptUrlTest {

        @Test
        @DisplayName("정상 조회 시 200 반환")
        void getManuscriptUrl_success_returns_200() {
            String mockUrl = "https://s3.amazonaws.com/bucket/file123.pdf";
            given(mentoringService.getManuscriptDownloadUrl(MENTORING_ID, USER_ID)).willReturn(mockUrl);

            ResponseEntity<BaseResponse<ManuscriptUrlResponse>> response =
                    mentoringController.getManuscriptUrl(MENTORING_ID, userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data().mentoringId()).isEqualTo(MENTORING_ID);
            assertThat(response.getBody().data().manuscriptUrl()).isEqualTo(mockUrl);
            verify(mentoringService, times(1)).getManuscriptDownloadUrl(MENTORING_ID, USER_ID);
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void getManuscriptUrl_message_check() {
            given(mentoringService.getManuscriptDownloadUrl(MENTORING_ID, USER_ID))
                    .willReturn("https://s3.amazonaws.com/bucket/file123.pdf");

            ResponseEntity<BaseResponse<ManuscriptUrlResponse>> response =
                    mentoringController.getManuscriptUrl(MENTORING_ID, userDetails);

            assertThat(response.getBody().message()).isEqualTo("원고 다운로드 URL 조회가 완료되었습니다");
        }
    }

    @Nested
    @DisplayName("PATCH /api/mentorings/{mentoringId}/complete - 멘토링 종료")
    class CompleteMentoringTest {

        @Test
        @DisplayName("정상 종료 시 200 반환")
        void completeMentoring_success_returns_200() {
            doNothing().when(mentoringService).completeMentoring(MENTORING_ID, USER_ID);

            ResponseEntity<BaseResponse<Void>> response =
                    mentoringController.completeMentoring(MENTORING_ID, userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data()).isNull();
            verify(mentoringService, times(1)).completeMentoring(MENTORING_ID, USER_ID);
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void completeMentoring_message_check() {
            doNothing().when(mentoringService).completeMentoring(MENTORING_ID, USER_ID);

            ResponseEntity<BaseResponse<Void>> response =
                    mentoringController.completeMentoring(MENTORING_ID, userDetails);

            assertThat(response.getBody().message()).isEqualTo("멘토링이 종료되었습니다");
        }
    }

    @Nested
    @DisplayName("GET /api/mentorings/{mentoringId} - 멘토링 상세 정보 조회")
    class GetMentoringDetailTest {

        @Test
        @DisplayName("정상 조회 시 200 반환")
        void getMentoringDetail_success_returns_200() {
            MentoringDetailResponse mockResponse = new MentoringDetailResponse(
                    MENTORING_ID,
                    "자바 백엔드 로드맵",
                    "김철수",
                    "전민우",
                    MentorshipStatus.ACCEPTED,
                    LocalDateTime.now(),
                    3,
                    List.of(
                            new MentoringDetailResponse.FeedbackInfo(
                                    1L, "1회차 피드백", 1,
                                    "ERD 설계 및 API 명세 작성", LocalDateTime.now()),
                            new MentoringDetailResponse.FeedbackInfo(
                                    2L, "2회차 피드백", 2,
                                    "Spring Security 설정", LocalDateTime.now())
                    )
            );
            given(mentoringService.getMentoringDetail(MENTORING_ID, USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentoringDetailResponse>> response =
                    mentoringController.getMentoringDetailV1(MENTORING_ID, userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("200");
            assertThat(response.getBody().data().mentoringId()).isEqualTo(MENTORING_ID);
            assertThat(response.getBody().data().novelTitle()).isEqualTo("자바 백엔드 로드맵");
            assertThat(response.getBody().data().mentorName()).isEqualTo("김철수");
            assertThat(response.getBody().data().menteeName()).isEqualTo("전민우");
            assertThat(response.getBody().data().totalSessions()).isEqualTo(3);
            assertThat(response.getBody().data().feedbacks()).hasSize(2);
            assertThat(response.getBody().data().feedbacks().get(0).title()).isEqualTo("1회차 피드백");
            assertThat(response.getBody().data().feedbacks().get(0).sessionNumber()).isEqualTo(1);
            assertThat(response.getBody().data().feedbacks().get(1).title()).isEqualTo("2회차 피드백");
            assertThat(response.getBody().data().feedbacks().get(1).sessionNumber()).isEqualTo(2);
            verify(mentoringService, times(1)).getMentoringDetail(MENTORING_ID, USER_ID);
        }

        @Test
        @DisplayName("피드백이 없는 경우 빈 리스트 반환")
        void getMentoringDetail_no_feedbacks_returns_empty_list() {
            MentoringDetailResponse mockResponse = new MentoringDetailResponse(
                    MENTORING_ID,
                    "자바 백엔드 로드맵",
                    "김철수",
                    "전민우",
                    MentorshipStatus.ACCEPTED,
                    LocalDateTime.now(),
                    0,
                    List.of()
            );
            given(mentoringService.getMentoringDetail(MENTORING_ID, USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentoringDetailResponse>> response =
                    mentoringController.getMentoringDetailV1(MENTORING_ID, userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().feedbacks()).isEmpty();
            assertThat(response.getBody().data().totalSessions()).isEqualTo(0);
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void getMentoringDetail_message_check() {
            MentoringDetailResponse mockResponse = new MentoringDetailResponse(
                    MENTORING_ID,
                    "자바 백엔드 로드맵",
                    "김철수", "전민우",
                    MentorshipStatus.ACCEPTED,
                    LocalDateTime.now(), 0, List.of()
            );
            given(mentoringService.getMentoringDetail(MENTORING_ID, USER_ID)).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentoringDetailResponse>> response =
                    mentoringController.getMentoringDetailV1(MENTORING_ID, userDetails);

            assertThat(response.getBody().message()).isEqualTo("멘토링 상세 정보 조회가 완료되었습니다");
        }
    }

    @Nested
    @DisplayName("POST /api/mentorings/{mentoringId}/feedbacks - 멘토링 피드백 작성")
    class CreateFeedbackTest {

        @Test
        @DisplayName("정상 피드백 작성 시 201 반환")
        void createFeedback_success_returns_201() {
            MentoringFeedbackRequest request = new MentoringFeedbackRequest(
                    "1회차 피드백",
                    "ERD 설계 및 API 명세 작성"
            );
            MentoringFeedbackResponse mockResponse = new MentoringFeedbackResponse(
                    1L, MENTORING_ID,
                    "1회차 피드백",
                    1,
                    "ERD 설계 및 API 명세 작성",
                    LocalDateTime.now()
            );
            given(mentoringService.createFeedback(eq(MENTORING_ID), eq(USER_ID), any())).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentoringFeedbackResponse>> response =
                    mentoringController.createFeedbackV1(MENTORING_ID, userDetails, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().status()).isEqualTo("201");
            assertThat(response.getBody().data().feedbackId()).isEqualTo(1L);
            assertThat(response.getBody().data().mentoringId()).isEqualTo(MENTORING_ID);
            assertThat(response.getBody().data().title()).isEqualTo("1회차 피드백");
            assertThat(response.getBody().data().sessionNumber()).isEqualTo(1);
            assertThat(response.getBody().data().content()).isEqualTo("ERD 설계 및 API 명세 작성");
            verify(mentoringService, times(1)).createFeedback(eq(MENTORING_ID), eq(USER_ID), any());
        }

        @Test
        @DisplayName("응답 메시지 확인")
        void createFeedback_message_check() {
            MentoringFeedbackRequest request = new MentoringFeedbackRequest(
                    "1회차 피드백",
                    "ERD 설계 및 API 명세 작성"
            );
            MentoringFeedbackResponse mockResponse = new MentoringFeedbackResponse(
                    1L, MENTORING_ID,
                    "1회차 피드백", 1,
                    "ERD 설계 및 API 명세 작성",
                    LocalDateTime.now()
            );
            given(mentoringService.createFeedback(eq(MENTORING_ID), eq(USER_ID), any())).willReturn(mockResponse);

            ResponseEntity<BaseResponse<MentoringFeedbackResponse>> response =
                    mentoringController.createFeedbackV1(MENTORING_ID, userDetails, request);

            assertThat(response.getBody().message()).isEqualTo("피드백이 등록되었습니다");
        }
    }
}