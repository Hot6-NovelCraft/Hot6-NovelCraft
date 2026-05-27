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
        Long viewCount
) {
    public static NovelRankingResponse of(int rank, Long novelId, String title, String authorNickname, Long viewCount
    ) {
        return new NovelRankingResponse(rank, novelId, title, authorNickname, viewCount);
    }
}
