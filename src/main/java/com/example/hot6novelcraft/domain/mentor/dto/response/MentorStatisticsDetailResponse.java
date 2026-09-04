package com.example.hot6novelcraft.domain.mentor.dto.response;

public record MentorStatisticsDetailResponse(
        long totalMentees,
        long completedSessions,
        double averageSatisfaction
) {
    public static MentorStatisticsDetailResponse of(long totalMentees, long completedSessions,
                                                    Double averageSatisfaction) {
        return new MentorStatisticsDetailResponse(
                totalMentees,
                completedSessions,
                averageSatisfaction != null ? Math.round(averageSatisfaction * 10.0) / 10.0 : 0.0
        );
    }
}
