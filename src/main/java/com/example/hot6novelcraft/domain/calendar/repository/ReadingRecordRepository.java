package com.example.hot6novelcraft.domain.calendar.repository;

import com.example.hot6novelcraft.domain.calendar.entity.ReadingRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReadingRecordRepository extends JpaRepository<ReadingRecord, Long> {

    // date, novelId 둘 다 없으면 전체 조회 / 각각 필터링 / 둘 다 필터링
    @Query("""
            SELECT r FROM ReadingRecord r
            WHERE r.userId = :userId
            AND (:date IS NULL OR r.readDate = :date)
            AND (:novelId IS NULL OR r.novelId = :novelId)
            ORDER BY r.readDate DESC, r.createdAt DESC
            """)
    Page<ReadingRecord> findByCondition(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("novelId") Long novelId,
            Pageable pageable
    );

    // 날짜 범위 내 전체 기록 조회
    @Query("""
            SELECT r FROM ReadingRecord r
            WHERE r.userId = :userId
            AND r.readDate BETWEEN :startDate AND :endDate
            """)
    List<ReadingRecord> findByUserIdAndDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 월별 전체 기록 조회 (통계용)
    @Query("""
            SELECT r FROM ReadingRecord r
            WHERE r.userId = :userId
            AND YEAR(r.readDate) = :year
            AND MONTH(r.readDate) = :month
            """)
    List<ReadingRecord> findByUserIdAndYearAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month
    );
}
