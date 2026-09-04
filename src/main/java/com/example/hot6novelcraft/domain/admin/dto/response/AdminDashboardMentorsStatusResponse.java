package com.example.hot6novelcraft.domain.admin.dto.response;

public record AdminDashboardMentorsStatusResponse(
        Long totalMentor        // 총 멘토/멘티 수
        , Long newMentorsToday  // 오늘 신규 멘토 수
) {
    public static AdminDashboardMentorsStatusResponse of(long totalMentor, long newMentorsToday) {
        return new AdminDashboardMentorsStatusResponse(
                totalMentor
                , newMentorsToday
        );
    }
}
