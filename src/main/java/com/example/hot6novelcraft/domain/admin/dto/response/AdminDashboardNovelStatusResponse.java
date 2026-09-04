package com.example.hot6novelcraft.domain.admin.dto.response;

public record AdminDashboardNovelStatusResponse(
        Long totalNovels
        , Long newNovelsToday
        , Long novelsByFilter
) {
    public static AdminDashboardNovelStatusResponse of(Long totalNovels, Long newNovelsToday, Long novelsByFilter) {
        return new AdminDashboardNovelStatusResponse(
                totalNovels
                , newNovelsToday
                , novelsByFilter
        );
    }
}
