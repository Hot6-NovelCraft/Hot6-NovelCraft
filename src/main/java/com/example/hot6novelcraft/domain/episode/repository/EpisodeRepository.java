package com.example.hot6novelcraft.domain.episode.repository;

import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface EpisodeRepository extends JpaRepository<Episode, Long>, CustomEpisodeRepository, CustomEpisodeStatRepository {

    // 회차 중복 확인
    boolean existsByNovelIdAndEpisodeNumberAndIsDeletedFalse(Long novelId, int episodeNumber);

    // 소설의 총 회차 수 조회
    int countByNovelIdAndIsDeletedFalse(Long novelId);

    // 해당 회차보다 큰 회차 번호가 존재하는지 확인(중간 회차 삭제 못하게!)
    boolean existsByNovelIdAndEpisodeNumberGreaterThanAndIsDeletedFalse(Long novelId, int episodeNumber);

    // 이전 회차 중 PUBLISHED 아닌 것 있는지 확인(순서대로 회차 발행 검증을 위해)
    boolean existsByNovelIdAndEpisodeNumberLessThanAndStatusNotAndIsDeletedFalse(Long novelId, int episodeNumber, EpisodeStatus status);

    // 좋아요 +
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Episode e SET e.likeCount = e.likeCount + 1 WHERE e.id = :episodeId")
    void incrementLikeCount(@Param("episodeId") Long episodeId);

    // 좋아요 -
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Episode e SET e.likeCount = e.likeCount - 1 WHERE e.id = :episodeId AND e.likeCount > 0")
    void decrementLikeCount(@Param("episodeId") Long episodeId);

    @Query("SELECT e.novelId, COUNT(e) FROM Episode e " +
            "WHERE e.novelId IN :novelIds AND e.isDeleted = false " +
            "GROUP BY e.novelId")
    List<Object[]> countByNovelIdsRaw(@Param("novelIds") List<Long> novelIds);

    default Map<Long, Long> countByNovelIds(List<Long> novelIds) {
        if (novelIds.isEmpty()) return Map.of();
        return countByNovelIdsRaw(novelIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    // 소설 목록 기준 PUBLISHED 에피소드 수 조회 (빈 리스트 방어)
    @Query("SELECT COUNT(e) FROM Episode e WHERE e.novelId IN :novelIds AND e.status = :status AND e.isDeleted = false")
    long countByNovelIdInAndStatusRaw(@Param("novelIds") List<Long> novelIds, @Param("status") EpisodeStatus status);

    default long countByNovelIdInAndStatus(List<Long> novelIds, EpisodeStatus status) {
        if (novelIds == null || novelIds.isEmpty()) return 0L;
        return countByNovelIdInAndStatusRaw(novelIds, status);
    }

    // 소설 목록 기준 likeCount 합산 조회 (빈 리스트 방어)
    @Query("SELECT COALESCE(SUM(e.likeCount), 0) FROM Episode e WHERE e.novelId IN :novelIds AND e.isDeleted = false")
    long sumLikeCountByNovelIdInRaw(@Param("novelIds") List<Long> novelIds);

    default long sumLikeCountByNovelIdIn(List<Long> novelIds) {
        if (novelIds == null || novelIds.isEmpty()) return 0L;
        return sumLikeCountByNovelIdInRaw(novelIds);
    }

    // 소설의 발행된 유료 회차 목록 조회 (구매용)
    @Query("SELECT e FROM Episode e WHERE e.novelId = :novelId AND e.status = 'PUBLISHED' AND e.isFree = false AND e.isDeleted = false ORDER BY e.episodeNumber ASC")
    List<Episode> findPublishedPaidEpisodesByNovelId(@Param("novelId") Long novelId);
}
