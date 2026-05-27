package com.example.hot6novelcraft.domain.library.dto.response;

import com.example.hot6novelcraft.domain.library.entity.Library;

import java.time.LocalDateTime;

public record LibraryListResponse(
        Long novelId,
        String title,
        String authorNickname,
        String coverImageUrl,
        String lastReadEpisodeTitle,  // null 허용
        Long totalEpisodes,         // Novel 테이블 Join 또는 별도 집계
        LocalDateTime addedAt
) {
    // Library 스냅샷만으로 구성하는 경우 (totalEpisodes는 별도 처리)
    public static LibraryListResponse from(Library library, Long totalEpisodes) {
        return new LibraryListResponse(
                library.getNovelId(),
                library.getNovelTitle(),
                library.getAuthorNickname(),
                library.getCoverImageUrl(),
                library.getLastReadEpisodeTitle(),
                totalEpisodes,
                library.getCreatedAt()
        );
    }
}
