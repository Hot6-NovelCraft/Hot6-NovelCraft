package com.example.hot6novelcraft.domain.report.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportStatus;
import com.example.hot6novelcraft.domain.report.entity.enums.ReportTargetType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReportTargetType targetType;

    @Builder
    private Report(Long reporterId, Long targetId, Long commentId,
                   String reason, ReportTargetType targetType) {
        this.reporterId = reporterId;
        this.targetId = targetId;
        this.commentId = commentId;
        this.reason = reason;
        this.targetType = targetType;
        this.status = ReportStatus.REGISTER; // 신고 생성시 무조건 접수상태
    }

    // 관리자가 처리상태 변경할 때 사용
    public void changeStatus(ReportStatus status) {
        this.status = status;
    }
}