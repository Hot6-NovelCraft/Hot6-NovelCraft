package com.example.hot6novelcraft.domain.admin.dto.response;

public record AdminDashboardUserStatusResponse(
        Long totalUsers
        , Long newUsersToday
        , Long filterUserRole
) {
    public static AdminDashboardUserStatusResponse of(Long totalUsers, Long newUsersToday, Long filterUserRole) {
        return new AdminDashboardUserStatusResponse(
                totalUsers
                , newUsersToday
                , filterUserRole
        );
    }
}