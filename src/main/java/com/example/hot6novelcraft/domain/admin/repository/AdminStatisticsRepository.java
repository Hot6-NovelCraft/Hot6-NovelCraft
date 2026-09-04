package com.example.hot6novelcraft.domain.admin.repository;

import com.example.hot6novelcraft.domain.admin.entity.AdminStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AdminStatisticsRepository extends JpaRepository<AdminStatistics, Long> {

    // 특정 날짜 통계 데이터 서치
    Optional<AdminStatistics> findByStatsDate(LocalDate statsDate);

    // today 멱등성 체크
    boolean existsByStatsDate(LocalDate statsDate);
}
