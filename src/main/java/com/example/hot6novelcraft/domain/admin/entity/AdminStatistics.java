package com.example.hot6novelcraft.domain.admin.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "admin_statistics")
public class AdminStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 통계 기준 날짜 (ex. 2026-05-02)
    @Column(nullable = false, unique = true)
    private LocalDate statsDate;

    // 해당 날짜의 신규 가입자 수
    @Column(nullable = false)
    private Long newUserCount;

    // 해당 날짜의 신작 소설 수
    @Column(nullable = false)
    private Long newNovelCount;

    // 해당 날짜의 신규 멘토 등록 수
    @Column(nullable = false)
    private Long newMentorCount;

    @Builder
    public AdminStatistics(LocalDate statsDate, Long newUserCount, Long newNovelCount, Long newMentorCount) {
        this.statsDate = statsDate;
        this.newUserCount = newUserCount != null ? newUserCount : 0L;
        this.newNovelCount = newNovelCount != null ? newNovelCount : 0L;
        this.newMentorCount = newMentorCount != null ? newMentorCount : 0L;
    }
}
