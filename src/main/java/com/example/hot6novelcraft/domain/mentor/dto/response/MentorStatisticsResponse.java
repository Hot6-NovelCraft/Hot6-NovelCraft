package com.example.hot6novelcraft.domain.mentor.dto.response;

public record MentorStatisticsResponse(
        long pendingCount,
        long thisMonthAcceptedCount,
        long thisMonthRejectedCount
) {
    public static MentorStatisticsResponse of(long pendingCount, long thisMonthAcceptedCount,
                                              long thisMonthRejectedCount) {
        return new MentorStatisticsResponse(pendingCount, thisMonthAcceptedCount, thisMonthRejectedCount);
    }
}
