package com.example.hot6novelcraft.domain.admin.dto.response;

public record AdminDashboardResponse(
        AdminDashboardUserStatusResponse userStatus
        , AdminDashboardNovelStatusResponse novelStatus
        , AdminDashboardMentorsStatusResponse mentorStatus
) {
    public static AdminDashboardResponse of(
            AdminDashboardUserStatusResponse userStatus
            , AdminDashboardNovelStatusResponse novelStatus
            , AdminDashboardMentorsStatusResponse mentorStatus
    ) {
        return new AdminDashboardResponse(userStatus, novelStatus, mentorStatus);
    }
}
