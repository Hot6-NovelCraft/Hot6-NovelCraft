package com.example.hot6novelcraft.domain.novel.repository;

import com.example.hot6novelcraft.domain.novel.entity.Novel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NovelRepository extends JpaRepository<Novel, Long>, CustomNovelRepository {

    // V1 - 소설 목록 조회 (IsDeleted확인)
    Page<Novel> findAllByIsDeletedFalse(Pageable pageable);

    // 조회수 증가
    @Modifying
    @Query("UPDATE Novel n SET n.viewCount = n.viewCount + :count WHERE n.id = :novelId")
    void incrementViewCountBy(@Param("novelId") Long novelId, @Param("count") long count);

    // 작가의 삭제되지 않은 소설 ID 목록 조회
    @Query("SELECT n.id FROM Novel n WHERE n.authorId = :authorId AND n.isDeleted = false")
    List<Long> findNovelIdsByAuthorId(@Param("authorId") Long authorId);

    // V2: soft-delete 조건 포함 조회 — findById 대신 사용
    Optional<Novel> findByIdAndIsDeletedFalse(Long id);

    // 찜 +
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Novel n SET n.bookmarkCount = n.bookmarkCount + 1 WHERE n.id = :novelId")
    void incrementBookmarkCount(@Param("novelId") Long novelId);

    // 찜 -
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Novel n SET n.bookmarkCount = n.bookmarkCount - 1 WHERE n.id = :novelId AND n.bookmarkCount > 0")
    void decrementBookmarkCount(@Param("novelId") Long novelId);

    // AI 추천 - 사용자가 좋아요한 회차 소설 태그 수집
    @Query("SELECT DISTINCT n.tags FROM Novel n JOIN Episode e ON e.novelId = n.id JOIN EpisodeLike el ON el.episodeId = e.id WHERE el.userId = :userId AND n.isDeleted = false")
    List<String> findTagsByUserLikedEpisodes(@Param("userId") Long userId);

    // AI 추천 - 추천 후보 소설 (발행, 완결 상태 / 최신순 50개)
    @Query("SELECT n FROM Novel n WHERE n.isDeleted = false AND n.status IN ('ONGOING', 'COMPLETED') ORDER BY n.createdAt DESC")
    Page<Novel> findTop50ForRecommendation(Pageable pageable);

    // AI 추천 - 특정 장르 소설 (선호 장르용)
    @Query("SELECT n FROM Novel n WHERE n.isDeleted = false AND n.status IN ('ONGOING', 'COMPLETED') AND n.genre = :genre ORDER BY n.viewCount DESC")
    Page<Novel> findByGenreForRecommendation(@Param("genre") String genre, Pageable pageable);

    // AI 추천 - 특정 장르 제외 소설 (다양성용)
    @Query("SELECT n FROM Novel n WHERE n.isDeleted = false AND n.status IN ('ONGOING', 'COMPLETED') AND n.genre != :genre ORDER BY n.viewCount DESC")
    Page<Novel> findExcludeGenreForRecommendation(@Param("genre") String genre, Pageable pageable);

    // AI 추천 - ID 목록으로 조회하되, 삭제되지 않고 공개된 소설만 조회 (랭킹 조회용)
    @Query("SELECT n FROM Novel n WHERE n.id IN :novelIds AND n.isDeleted = false AND n.status IN ('ONGOING', 'COMPLETED')")
    List<Novel> findActiveNovelsByIds(@Param("novelIds") List<Long> novelIds);

    // AI 추천 - DB fallback
    @Query("SELECT n FROM Novel n WHERE n.isDeleted = false AND n.status IN ('ONGOING', 'COMPLETED') ORDER BY n.viewCount DESC")
    Page<Novel> findFallbackNovels(Pageable pageable);
}
