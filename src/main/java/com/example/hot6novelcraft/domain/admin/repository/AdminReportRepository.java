package com.example.hot6novelcraft.domain.admin.repository;

import com.example.hot6novelcraft.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminReportRepository extends JpaRepository<Report, Long>, CustomAdminReportRepository {
}