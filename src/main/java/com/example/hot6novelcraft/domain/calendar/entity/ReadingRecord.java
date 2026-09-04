package com.example.hot6novelcraft.domain.calendar.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.calendar.entity.enums.ReadingStatus;
import com.example.hot6novelcraft.domain.calendar.entity.enums.RecordSource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "reading_records") // ERD의 테이블명 반영
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // 회원 아이디 직접 참조 (연관관계 금지 컨벤션)

    private Long novelId; // 플랫폼 내 소설일 경우 ID 저장

    @Column(length = 200)
    private String title; // 도서명(외부)

    @Column(length = 100)
    private String authorName; // 저자명(외부)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordSource source; // PLATFORM, EXTERNAL 구분

    @Column(nullable = false)
    private LocalDate readDate; // 독서 일자

    // API 요구사항 및 실무적인 메모 기능을 위해 추가 유지
    @Column(columnDefinition = "TEXT")
    private String note;

    private Integer readPage;    // 읽은 페이지 수

    private Integer totalPage;   // 전체 페이지 수

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReadingStatus readingStatus;

    private ReadingRecord(Long userId, Long novelId, String title, String authorName, RecordSource source, LocalDate readDate, String note,
                          Integer readPage, Integer totalPage, ReadingStatus readingStatus) {
        this.userId = userId;
        this.novelId = novelId;
        this.title = title;
        this.authorName = authorName;
        this.source = source;
        this.readDate = readDate;
        this.note = note;
        this.readPage = readPage;
        this.totalPage = totalPage;
        this.readingStatus = readingStatus;
    }

    public static ReadingRecord create(Long userId, Long novelId, String title, String authorName, RecordSource source, LocalDate readDate, String note,
                                       Integer readPage, Integer totalPage, ReadingStatus readingStatus) {
        return new ReadingRecord(userId, novelId, title, authorName, source, readDate, note, readPage, totalPage, readingStatus);
    }
}
