package com.example.hot6novelcraft.domain.admin.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.admin.entity.enums.ResultStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "report_management",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_report_management_report_id", columnNames = "report_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportManagement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "admin_memo", nullable = false, length = 100)
    private String adminMemo;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false)
    private ResultStatus resultStatus;

    @Builder
    private ReportManagement(Long reportId, Long targetId, Long adminId,
                             String adminMemo, ResultStatus resultStatus) {
        this.reportId = reportId;
        this.targetId = targetId;
        this.adminId = adminId;
        this.adminMemo = adminMemo;
        this.resultStatus = resultStatus;
    }
}