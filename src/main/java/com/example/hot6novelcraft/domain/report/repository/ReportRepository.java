package com.example.hot6novelcraft.domain.report.repository;

import com.example.hot6novelcraft.domain.report.entity.Report;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 24시간 내 동일 댓글 신고 여부
    boolean existsByReporterIdAndCommentIdAndCreatedAtAfter(Long reporterId, Long commentId, LocalDateTime since);

    // 24시간 내 동일 유저 신고 여부
    boolean existsByReporterIdAndTargetIdAndTargetTypeAndCreatedAtAfter(Long reporterId, Long targetId, ReportTargetType targetType, LocalDateTime since);
}