package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardMentorsStatusResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardNovelStatusResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardUserStatusResponse;
import com.example.hot6novelcraft.domain.admin.repository.CustomAdminRepository;
import com.example.hot6novelcraft.domain.admin.repository.AdminStatisticsRepository;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDashboardService 단위 테스트")
class AdminDashboardServiceTest {

    @Mock
    private CustomAdminRepository adminRepository;

    @Mock
    private AdminCacheService adminCacheService;

    @Mock
    private AdminStatisticsRepository adminStatisticsRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    private AdminDashboardUserStatusResponse userStatus(long total, long newToday, long filtered) {
        return AdminDashboardUserStatusResponse.of(total, newToday, filtered);
    }

    private AdminDashboardNovelStatusResponse novelStatus(long total, long newToday, long filtered) {
        return AdminDashboardNovelStatusResponse.of(total, newToday, filtered);
    }

    private AdminDashboardMentorsStatusResponse mentorStatus(long total, long newToday) {
        return AdminDashboardMentorsStatusResponse.of(total, newToday);
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessCase {

        @Test
        @DisplayName("필터 없이 전체 통계 조회 성공")
        void getDashboardStats_전체조회_성공() {
            given(adminRepository.getIntegratedUserStatus(null))
                    .willReturn(userStatus(100L, 5L, 90L));
            given(adminRepository.getIntegratedNovelStatus(null, null, null))
                    .willReturn(novelStatus(200L, 3L, 200L));
            given(adminRepository.getIntegratedMentorsStatus())
                    .willReturn(mentorStatus(30L, 1L));

            AdminDashboardResponse result =
                    adminDashboardService.getDashboardStatusIntegrated(null, null, null, null);

            assertThat(result).isNotNull();
            assertThat(result.userStatus().totalUsers()).isEqualTo(100L);
            assertThat(result.userStatus().newUsersToday()).isEqualTo(5L);
            assertThat(result.userStatus().filterUserRole()).isEqualTo(90L);
            assertThat(result.novelStatus().totalNovels()).isEqualTo(200L);
            assertThat(result.novelStatus().newNovelsToday()).isEqualTo(3L);
            assertThat(result.novelStatus().novelsByFilter()).isEqualTo(200L);
            assertThat(result.mentorStatus().totalMentor()).isEqualTo(30L);
            assertThat(result.mentorStatus().newMentorsToday()).isEqualTo(1L);

            verify(adminRepository, times(1)).getIntegratedUserStatus(null);
            verify(adminRepository, times(1)).getIntegratedNovelStatus(null, null, null);
            verify(adminRepository, times(1)).getIntegratedMentorsStatus();
        }

        @Test
        @DisplayName("role=READER 필터 조회 성공")
        void getDashboardStats_독자필터_성공() {
            given(adminRepository.getIntegratedUserStatus(UserRole.READER))
                    .willReturn(userStatus(100L, 5L, 60L));
            given(adminRepository.getIntegratedNovelStatus(null, null, null))
                    .willReturn(novelStatus(200L, 3L, 200L));
            given(adminRepository.getIntegratedMentorsStatus())
                    .willReturn(mentorStatus(30L, 1L));

            AdminDashboardResponse result =
                    adminDashboardService.getDashboardStatusIntegrated(UserRole.READER, null, null, null);

            assertThat(result.userStatus().filterUserRole()).isEqualTo(60L);
            verify(adminRepository, times(1)).getIntegratedUserStatus(UserRole.READER);
        }

        @Test
        @DisplayName("novelStatus=ONGOING 필터 조회 성공")
        void getDashboardStats_연재중필터_성공() {
            given(adminRepository.getIntegratedUserStatus(null))
                    .willReturn(userStatus(100L, 5L, 90L));
            given(adminRepository.getIntegratedNovelStatus(NovelStatus.ONGOING.name(), null, null))
                    .willReturn(novelStatus(200L, 3L, 100L));
            given(adminRepository.getIntegratedMentorsStatus())
                    .willReturn(mentorStatus(30L, 1L));

            AdminDashboardResponse result =
                    adminDashboardService.getDashboardStatusIntegrated(null, NovelStatus.ONGOING.name(), null, null);

            assertThat(result.novelStatus().novelsByFilter()).isEqualTo(100L);
            verify(adminRepository, times(1)).getIntegratedNovelStatus(NovelStatus.ONGOING.name(), null, null);
        }

        @Test
        @DisplayName("isDeleted=true 삭제 소설 필터 조회 성공")
        void getDashboardStats_삭제소설필터_성공() {
            given(adminRepository.getIntegratedUserStatus(null))
                    .willReturn(userStatus(100L, 5L, 90L));
            given(adminRepository.getIntegratedNovelStatus(null, null, true))
                    .willReturn(novelStatus(200L, 3L, 10L));
            given(adminRepository.getIntegratedMentorsStatus())
                    .willReturn(mentorStatus(30L, 1L));

            AdminDashboardResponse result =
                    adminDashboardService.getDashboardStatusIntegrated(null, null, null, true);

            assertThat(result.novelStatus().novelsByFilter()).isEqualTo(10L);
            verify(adminRepository, times(1)).getIntegratedNovelStatus(null, null, true);
        }

        @Test
        @DisplayName("데이터 없을 때 0으로 반환 성공")
        void getDashboardStats_데이터없음_0반환() {
            given(adminRepository.getIntegratedUserStatus(null))
                    .willReturn(userStatus(0L, 0L, 0L));
            given(adminRepository.getIntegratedNovelStatus(null, null, null))
                    .willReturn(novelStatus(0L, 0L, 0L));
            given(adminRepository.getIntegratedMentorsStatus())
                    .willReturn(mentorStatus(0L, 0L));

            AdminDashboardResponse result =
                    adminDashboardService.getDashboardStatusIntegrated(null, null, null, null);

            assertThat(result.userStatus().totalUsers()).isZero();
            assertThat(result.novelStatus().totalNovels()).isZero();
            assertThat(result.mentorStatus().totalMentor()).isZero();
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailCase {

        @Test
        @DisplayName("Repository 예외 발생 시 전파")
        void getDashboardStats_Repository예외_전파() {
            given(adminRepository.getIntegratedUserStatus(null))
                    .willThrow(new RuntimeException("DB 연결 실패"));

            org.junit.jupiter.api.Assertions.assertThrows(
                    RuntimeException.class,
                    () -> adminDashboardService.getDashboardStatusIntegrated(null, null, null, null)
            );
        }
    }
}