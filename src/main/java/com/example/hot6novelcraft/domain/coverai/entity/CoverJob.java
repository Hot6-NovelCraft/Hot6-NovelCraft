package com.example.hot6novelcraft.domain.coverai.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.coverai.entity.enums.CoverJobStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cover_jobs")
public class CoverJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String jobId;

    @Column(nullable = false)
    private Long novelId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoverJobStatus status;

    @Column(length = 500)
    private String coverImageUrl;

    @Column(length = 500)
    private String errorMessage;

    public static CoverJob create(String jobId, Long novelId, Long userId) {
        CoverJob job = new CoverJob();
        job.jobId = jobId;
        job.novelId = novelId;
        job.userId = userId;
        job.status = CoverJobStatus.PENDING;
        return job;
    }

    public void processing() {
        this.status = CoverJobStatus.PROCESSING;
    }

    public void complete(String coverImageUrl) {
        this.status = CoverJobStatus.COMPLETED;
        this.coverImageUrl = coverImageUrl;
        this.errorMessage = null;
    }

    public void fail(String errorMessage) {
        this.status = CoverJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.coverImageUrl = null;
    }
}