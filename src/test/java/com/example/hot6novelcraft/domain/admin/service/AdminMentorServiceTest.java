package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminPendingMentorResponse;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminMentorService 단위 테스트")
class AdminMentorServiceTest {

    @Mock
    private MentorRepository mentorRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminMentorService adminMentorService;

    // =============================================
    // ✅ 성공 케이스
    // =============================================
    @Nested
    @DisplayName("성공 케이스")
    class SuccessCase {

        @Test
        @DisplayName("PROFICIENT 등급 심사 대기 목록 조회 성공")
        void getPendingProficientMentors_성공() {
            // given
            // Mock Mentor 객체 생성 (Mentor는 생성자가 private이라 mock으로 처리)
            Mentor mockMentor = mock(Mentor.class);
            given(mockMentor.getUserId()).willReturn(1L);
            given(mockMentor.getStatus()).willReturn(MentorStatus.PENDING);
            given(mockMentor.getCareerLevel()).willReturn(CareerLevel.PROFICIENT);

            // Mock User 객체 생성
            User mockUser = User.register(
                    "mentor@test.com", "password", "멘토유저",
                    "01011111111", null, UserRole.AUTHOR
            );

            given(mentorRepository.findAllByStatusAndCareerLevel(
                    MentorStatus.PENDING, CareerLevel.PROFICIENT))
                    .willReturn(List.of(mockMentor));
            given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

            // when
            List<AdminPendingMentorResponse> result =
                    adminMentorService.getPendingProficientMentors();

            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);

            // Repository 호출 검증
            verify(mentorRepository, times(1))
                    .findAllByStatusAndCareerLevel(MentorStatus.PENDING, CareerLevel.PROFICIENT);
            verify(userRepository, times(1)).findById(1L);
        }

        @Test
        @DisplayName("대기 목록 없을 때 빈 리스트 반환")
        void getPendingProficientMentors_빈리스트_반환() {
            // given
            given(mentorRepository.findAllByStatusAndCareerLevel(
                    MentorStatus.PENDING, CareerLevel.PROFICIENT))
                    .willReturn(List.of()); // 빈 리스트

            // when
            List<AdminPendingMentorResponse> result =
                    adminMentorService.getPendingProficientMentors();

            // then
            assertThat(result).isEmpty();
            // 빈 리스트면 userRepository는 호출 안 됨
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("멘토 승인 처리 성공")
        void approveMentor_성공() {
            // given
            Long mentorId = 1L;
            Mentor mockMentor = mock(Mentor.class);
            given(mentorRepository.findById(mentorId)).willReturn(Optional.of(mockMentor));

            // when
            adminMentorService.approveMentor(mentorId);

            // then - approve() 메서드가 실제로 호출됐는지 검증
            verify(mentorRepository, times(1)).findById(mentorId);
            verify(mockMentor, times(1)).approve();
        }

        @Test
        @DisplayName("멘토 거절 처리 성공")
        void rejectMentor_성공() {
            // given
            Long mentorId = 1L;
            String rejectReason = "경력 사항 미흡";
            Mentor mockMentor = mock(Mentor.class);
            given(mentorRepository.findById(mentorId)).willReturn(Optional.of(mockMentor));

            // when
            adminMentorService.rejectMentor(mentorId, rejectReason);

            // then - reject()가 rejectReason과 함께 호출됐는지 검증
            verify(mentorRepository, times(1)).findById(mentorId);
            verify(mockMentor, times(1)).reject(rejectReason);
        }
    }

    // =============================================
    // ❌ 실패 케이스
    // =============================================
    @Nested
    @DisplayName("실패 케이스")
    class FailCase {

        @Test
        @DisplayName("대기 목록 조회 중 유저 없으면 예외 발생")
        void getPendingProficientMentors_유저없음_예외() {
            // given
            Mentor mockMentor = mock(Mentor.class);
            given(mockMentor.getUserId()).willReturn(999L); // 존재하지 않는 userId

            given(mentorRepository.findAllByStatusAndCareerLevel(
                    MentorStatus.PENDING, CareerLevel.PROFICIENT))
                    .willReturn(List.of(mockMentor));

            // userId=999로 조회 시 empty 반환 → 예외 발생
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> adminMentorService.getPendingProficientMentors())
                    .isInstanceOf(ServiceErrorException.class);

            verify(userRepository, times(1)).findById(999L);
        }

        @Test
        @DisplayName("존재하지 않는 mentorId로 승인 시 예외 발생")
        void approveMentor_멘토없음_예외() {
            // given
            Long invalidMentorId = 999L;
            given(mentorRepository.findById(invalidMentorId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> adminMentorService.approveMentor(invalidMentorId))
                    .isInstanceOf(ServiceErrorException.class);

            // 멘토 못 찾으면 approve()는 호출 안 됨
            verify(mentorRepository, times(1)).findById(invalidMentorId);
        }

        @Test
        @DisplayName("존재하지 않는 mentorId로 거절 시 예외 발생")
        void rejectMentor_멘토없음_예외() {
            // given
            Long invalidMentorId = 999L;
            String rejectReason = "경력 사항 미흡";
            given(mentorRepository.findById(invalidMentorId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> adminMentorService.rejectMentor(invalidMentorId, rejectReason))
                    .isInstanceOf(ServiceErrorException.class);

            verify(mentorRepository, times(1)).findById(invalidMentorId);
        }

        @Test
        @DisplayName("거절 사유 없이 거절 처리 시 mentor.reject()에 null 전달")
        void rejectMentor_거절사유없음_null전달() {
            // given
            Long mentorId = 1L;
            Mentor mockMentor = mock(Mentor.class);
            given(mentorRepository.findById(mentorId)).willReturn(Optional.of(mockMentor));

            // when - rejectReason null로 전달
            adminMentorService.rejectMentor(mentorId, null);

            // then - null이 그대로 전달되는지 검증
            // (reject() 내부에서 null 처리는 Mentor Entity 책임)
            verify(mockMentor, times(1)).reject(null);
        }
    }
}