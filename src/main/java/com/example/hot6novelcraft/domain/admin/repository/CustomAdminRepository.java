package com.example.hot6novelcraft.domain.admin.repository;

import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardMentorsStatusResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardNovelStatusResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardUserStatusResponse;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;

public interface CustomAdminRepository {

    /**
     * ======= v1 쿼리 분할 =======
     **/
    // 회원 통계
    Long countTotalUsers();

    Long countNewUsersToday();

    Long countUsersByRole(UserRole role);

    // 소설 통계
    Long countTotalNovels(String novelStatus);

    Long countNewNovelsToday();

    Long countNovelsByFilter(NovelStatus novelStatusEnum, Boolean isDeleted);

    // 멘토링 통계
    Long countTotalMentors();

    Long countNewMentorsToday();

    /**
     * ======= v2 쿼리 병합 =======
     **/
    AdminDashboardUserStatusResponse getIntegratedUserStatus(UserRole role);

    AdminDashboardNovelStatusResponse getIntegratedNovelStatus(String totalStatusFilter, NovelStatus filterStatus, Boolean isDeleted);

    AdminDashboardMentorsStatusResponse getIntegratedMentorsStatus();

    /**
     * ======= v3 쿼리 병합 + Redis 신규 통계 =======
     **/
    AdminDashboardUserStatusResponse getUserStatusForeLive(UserRole role);
    AdminDashboardNovelStatusResponse getNovelStatusForLive(String totalStatusFilter, NovelStatus filterStatus, Boolean isDeleted);
    AdminDashboardMentorsStatusResponse getMentorsStatusForLive();
}