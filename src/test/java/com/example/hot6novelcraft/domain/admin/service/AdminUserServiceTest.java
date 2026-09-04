package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminResponse;
import com.example.hot6novelcraft.domain.user.entity.User;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService 단위 테스트")
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    // =============================================
    // 성공 케이스
    // =============================================
    @Nested
    @DisplayName("성공 케이스")
    class SuccessCase {

        @Test
        @DisplayName("PENDING_ADMIN 목록 조회 성공")
        void getPendingAdmins_성공() {
            // given
            User pendingAdmin1 = User.register(
                    "admin1@test.com", "password", "관리자1",
                    "01011111111", null, UserRole.PENDING_ADMIN);
            User pendingAdmin2 = User.register(
                    "admin2@test.com", "password", "관리자2",
                    "01022222222", null, UserRole.PENDING_ADMIN);

            given(userRepository.findAllByRole(UserRole.PENDING_ADMIN))
                    .willReturn(List.of(pendingAdmin1, pendingAdmin2));

            // when
            List<AdminResponse> result = adminUserService.getPendingAdmins();

            // then
            assertThat(result).hasSize(2);
            verify(userRepository, times(1)).findAllByRole(UserRole.PENDING_ADMIN);
        }

        @Test
        @DisplayName("PENDING_ADMIN 목록 없을 때 빈 리스트 반환")
        void getPendingAdmins_빈리스트_반환() {
            // given
            given(userRepository.findAllByRole(UserRole.PENDING_ADMIN))
                    .willReturn(List.of());

            // when
            List<AdminResponse> result = adminUserService.getPendingAdmins();

            // then
            assertThat(result).isEmpty();
            verify(userRepository, times(1)).findAllByRole(UserRole.PENDING_ADMIN);
        }

        @Test
        @DisplayName("관리자 승인 성공 - PENDING_ADMIN → ADMIN")
        void approvePendingAdmin_성공() {
            // given
            Long userId = 1L;
            // updateRoleIfCurrent가 1 반환 = 업데이트 성공
            given(userRepository.updateRoleIfCurrent(
                    userId, UserRole.PENDING_ADMIN, UserRole.ADMIN))
                    .willReturn(1);

            // when - 예외 없이 정상 종료되는지 검증
            adminUserService.approvePendingAdmin(userId);

            // then
            verify(userRepository, times(1))
                    .updateRoleIfCurrent(userId, UserRole.PENDING_ADMIN, UserRole.ADMIN);
        }

        @Test
        @DisplayName("관리자 거절 성공 - PENDING_ADMIN → REJECTED_ADMIN")
        void rejectPendingAdmin_성공() {
            // given
            Long userId = 1L;
            given(userRepository.updateRoleIfCurrent(
                    userId, UserRole.PENDING_ADMIN, UserRole.REJECTED_ADMIN))
                    .willReturn(1);

            // when
            adminUserService.rejectPendingAdmin(userId);

            // then
            verify(userRepository, times(1))
                    .updateRoleIfCurrent(userId, UserRole.PENDING_ADMIN, UserRole.REJECTED_ADMIN);
        }
    }

    // =============================================
    // 실패 케이스
    // =============================================
    @Nested
    @DisplayName("실패 케이스")
    class FailCase {

        @Test
        @DisplayName("승인 - 존재하지 않는 userId면 예외 발생")
        void approvePendingAdmin_유저없음_예외() {
            // given
            Long invalidUserId = 999L;
            // updateRoleIfCurrent가 0 반환 = 업데이트 실패 (유저 없음)
            given(userRepository.updateRoleIfCurrent(
                    invalidUserId, UserRole.PENDING_ADMIN, UserRole.ADMIN))
                    .willReturn(0);

            // when & then
            assertThatThrownBy(() -> adminUserService.approvePendingAdmin(invalidUserId))
                    .isInstanceOf(ServiceErrorException.class);

            verify(userRepository, times(1))
                    .updateRoleIfCurrent(invalidUserId, UserRole.PENDING_ADMIN, UserRole.ADMIN);
        }

        @Test
        @DisplayName("승인 - 이미 승인/거절된 관리자면 예외 발생")
        void approvePendingAdmin_이미처리된관리자_예외() {
            // given
            Long alreadyApprovedUserId = 1L;
            // PENDING_ADMIN이 아닌 상태라 조건 불일치 → 0 반환
            given(userRepository.updateRoleIfCurrent(
                    alreadyApprovedUserId, UserRole.PENDING_ADMIN, UserRole.ADMIN))
                    .willReturn(0);

            // when & then
            assertThatThrownBy(() -> adminUserService.approvePendingAdmin(alreadyApprovedUserId))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("거절 - 존재하지 않는 userId면 예외 발생")
        void rejectPendingAdmin_유저없음_예외() {
            // given
            Long invalidUserId = 999L;
            given(userRepository.updateRoleIfCurrent(
                    invalidUserId, UserRole.PENDING_ADMIN, UserRole.REJECTED_ADMIN))
                    .willReturn(0);

            // when & then
            assertThatThrownBy(() -> adminUserService.rejectPendingAdmin(invalidUserId))
                    .isInstanceOf(ServiceErrorException.class);

            verify(userRepository, times(1))
                    .updateRoleIfCurrent(invalidUserId, UserRole.PENDING_ADMIN, UserRole.REJECTED_ADMIN);
        }

        @Test
        @DisplayName("거절 - 이미 승인/거절된 관리자면 예외 발생")
        void rejectPendingAdmin_이미처리된관리자_예외() {
            // given
            Long alreadyRejectedUserId = 1L;
            given(userRepository.updateRoleIfCurrent(
                    alreadyRejectedUserId, UserRole.PENDING_ADMIN, UserRole.REJECTED_ADMIN))
                    .willReturn(0);

            // when & then
            assertThatThrownBy(() -> adminUserService.rejectPendingAdmin(alreadyRejectedUserId))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }
}