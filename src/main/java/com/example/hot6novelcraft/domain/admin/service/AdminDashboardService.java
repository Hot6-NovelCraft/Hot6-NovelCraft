package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.AdminExceptionEnum;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardMentorsStatusResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardNovelStatusResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardUserStatusResponse;
import com.example.hot6novelcraft.domain.admin.entity.AdminStatistics;
import com.example.hot6novelcraft.domain.admin.repository.AdminStatisticsRepository;
import com.example.hot6novelcraft.domain.admin.repository.CustomAdminRepository;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j(topic = "AdminDashboardService")
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final CustomAdminRepository adminRepository;
    private final AdminCacheService adminCacheService;
    private final AdminStatisticsRepository adminStatisticsRepository;

    /**
     *  v1 쿼리 분할 (DB I/O 8회) + indexing
     */
    public AdminDashboardResponse getDashboardStatusSeparated(
            UserRole role
            , String novelStatus
            , NovelStatus novelFilter
            , Boolean isDeleted
    ) {

        // 회원 통계 대시보드 (QueryDSL + indexing)
        AdminDashboardUserStatusResponse userStatus = AdminDashboardUserStatusResponse.of(
                adminRepository.countTotalUsers()
                , adminRepository.countNewUsersToday()
                , adminRepository.countUsersByRole(role)
        );

        // 소설 통계 대시보드 (QueryDSL + indexing)
        AdminDashboardNovelStatusResponse novelStatusResponse = AdminDashboardNovelStatusResponse.of(
                adminRepository.countTotalNovels(novelStatus)
                , adminRepository.countNewNovelsToday()
                , adminRepository.countNovelsByFilter(novelFilter, isDeleted)
        );

        // 멘토 통계 대시보드 (QueryDSL + indexing)
        AdminDashboardMentorsStatusResponse mentorStatus = AdminDashboardMentorsStatusResponse.of(
                adminRepository.countTotalMentors()
                , adminRepository.countNewMentorsToday()
        );

        return AdminDashboardResponse.of(userStatus, novelStatusResponse, mentorStatus);
    }

    /**
     * v2 쿼리 병합 (DB I/O 3회) + indexing
     */
    public AdminDashboardResponse getDashboardStatusIntegrated(
            UserRole role
            , String novelStatus
            , NovelStatus novelFilter
            , Boolean isDeleted
    ) {
        AdminDashboardUserStatusResponse userStatusResponse = adminRepository.getIntegratedUserStatus(role);
        AdminDashboardNovelStatusResponse novelStatusResponse = adminRepository.getIntegratedNovelStatus(novelStatus, novelFilter, isDeleted);
        AdminDashboardMentorsStatusResponse mentorStatusResponse = adminRepository.getIntegratedMentorsStatus();

        return AdminDashboardResponse.of(userStatusResponse, novelStatusResponse, mentorStatusResponse);
    }

    /**
     * v3 신규 회원 및 신작 소설
     * 과거 데이터 스냅샷 + Redis 캐싱 + 쿼리 병합 + indexing 통합
     * "오늘" 실시간 기준 데이터 변환
     */
    public AdminDashboardResponse getLiveDashboard(
            UserRole role
            , String novelStatus
            , NovelStatus novelFilter
            , Boolean isDeleted
    ) {
        // "오늘" 신규 지표는 Redis에서 즉시 조회
        Long newUsersToday = adminCacheService.getNewUsersByDate();
        Long newNovelsToday = adminCacheService.getNewNovelsByDate();
        Long newMentosToday = adminCacheService.getNewMentosByDate();

        // 전체 지표는 병합 쿼리 사용
        AdminDashboardUserStatusResponse dbUsersStatus = adminRepository.getIntegratedUserStatus(role);
        AdminDashboardNovelStatusResponse dbNovelsStatus = adminRepository.getIntegratedNovelStatus(novelStatus, novelFilter, isDeleted);
        AdminDashboardMentorsStatusResponse dbMentorsStatus = adminRepository.getIntegratedMentorsStatus();

        return AdminDashboardResponse.of(
                AdminDashboardUserStatusResponse.of(dbUsersStatus.totalUsers(), newUsersToday, dbUsersStatus.filterUserRole()),
                AdminDashboardNovelStatusResponse.of(dbNovelsStatus.totalNovels(), newNovelsToday, dbNovelsStatus.novelsByFilter()),
                AdminDashboardMentorsStatusResponse.of(dbMentorsStatus.totalMentor(), newMentosToday)
        );
    }

    /**
     * 과거 통계 분석용
     * 스케쥴러가 저장해둔 AdminStatistics 통계 DB 테이블만 조회
     */
    public AdminDashboardResponse getHistoryDashBoard(LocalDate targetDate) {

        // 통계 테이블에서 1줄만
        AdminStatistics pastStats = adminStatisticsRepository.findByStatsDate(targetDate)
                .orElseThrow(() -> new ServiceErrorException(AdminExceptionEnum.NOT_FOUND_DATA_AT_DATE));

        // 과거 신규 회원 및 소설, 멘토 데이터만 출력
        AdminDashboardUserStatusResponse userStatusResponse = AdminDashboardUserStatusResponse.of(0L, pastStats.getNewUserCount(), 0L);
        AdminDashboardNovelStatusResponse novelStatusResponse = AdminDashboardNovelStatusResponse.of(0L, pastStats.getNewNovelCount(), 0L);
        AdminDashboardMentorsStatusResponse mentorsStatusResponse = AdminDashboardMentorsStatusResponse.of(0L, pastStats.getNewMentorCount());

        return AdminDashboardResponse.of(userStatusResponse, novelStatusResponse, mentorsStatusResponse);
    }
}
