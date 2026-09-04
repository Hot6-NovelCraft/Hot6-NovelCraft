package com.example.hot6novelcraft.domain.admin.repository;

import com.example.hot6novelcraft.domain.admin.entity.ReportManagement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportManagementRepository extends JpaRepository<ReportManagement, Long> {

    // 신고대상 유저 누적 처리 횟수
    long countByTargetId(Long targetId);

    // 동일 신고건 중복처리 방지
    boolean existsByReportId(Long reportId);
}