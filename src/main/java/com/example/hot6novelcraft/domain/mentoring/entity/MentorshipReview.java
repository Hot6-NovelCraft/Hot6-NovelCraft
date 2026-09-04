package com.example.hot6novelcraft.domain.mentoring.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mentorship_reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mentorship_id"}))
public class MentorshipReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long mentorshipId;

    @Column(nullable = false)
    private Long reviewerId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String content;

    @Builder
    public MentorshipReview(Long mentorshipId, Long reviewerId,
                            int rating, String content) {
        this.mentorshipId = mentorshipId;
        this.reviewerId = reviewerId;
        this.rating = rating;
        this.content = content;
    }
}