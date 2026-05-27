package com.example.hot6novelcraft.domain.novel.dto.response;

/**
 * ===============================
 * 작성자 - 서하나
 * ===================================
 */
public record NovelRankingResponse(
        int rank,
        Long novelId,
        String title,
        String authorNickname,
        Long viewCount,
        String coverImageUrl
) {
    public static NovelRankingResponse of(int rank, Long novelId, String title, String authorNickname, Long viewCount, String coverImageUrl
    ) {
        return new NovelRankingResponse(rank, novelId, title, authorNickname, viewCount, coverImageUrl);
    }
}
