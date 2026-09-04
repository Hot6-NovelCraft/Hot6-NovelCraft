package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.domain.exchange.entity.Revenue;
import com.example.hot6novelcraft.domain.exchange.entity.enums.RevenueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RevenueRepository extends JpaRepository<Revenue, Long>, RevenueRepositoryCustom {

    /**
     * 특정 수익 유형들의 총 금액 합산
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Revenue r WHERE r.authorId = :authorId AND r.type IN :types")
    Integer sumAmountByAuthorIdAndTypeIn(@Param("authorId") Long authorId, @Param("types") java.util.List<RevenueType> types);

    /**
     * 특정 수익 유형의 총 금액 합산
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Revenue r WHERE r.authorId = :authorId AND r.type = :type")
    Integer sumAmountByAuthorIdAndType(@Param("authorId") Long authorId, @Param("type") RevenueType type);

    /**
     * 가장 최근 Revenue의 잔액 조회 (잔액 빠른 조회용)
     */
    Optional<Revenue> findTopByAuthorIdOrderByCreatedAtDesc(Long authorId);
}