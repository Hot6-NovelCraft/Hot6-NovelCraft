package com.example.hot6novelcraft.domain.mentor.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// V2: (mentorship_id, session_number) 유니크 제약 추가 — 동시성 이슈 방어
@Entity
@Table(
        name = "mentorship_feedbacks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mentorship_id", "session_number"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MentorFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long mentorshipId;

    @Column(nullable = false)
    private Long authorId;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(nullable = false)
    private int sessionNumber;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private MentorFeedback(Long mentorshipId, Long authorId, String title,
                           int sessionNumber, String content) {
        this.mentorshipId = mentorshipId;
        this.authorId = authorId;
        this.title = title;
        this.sessionNumber = sessionNumber;
        this.content = content;
    }

    public static MentorFeedback create(Long mentorshipId, Long authorId, String title,
                                        int sessionNumber, String content) {
        return new MentorFeedback(mentorshipId, authorId, title, sessionNumber, content);
    }
}