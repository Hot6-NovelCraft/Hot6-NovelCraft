package com.example.hot6novelcraft.domain.mentor.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mentor_career_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MentorCareerHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long mentorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareerLevel previousLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareerLevel newLevel;

    @Column(nullable = false, length = 200)
    private String changeReason;

    private MentorCareerHistory(Long mentorId, CareerLevel previousLevel,
                                CareerLevel newLevel, String changeReason) {
        this.mentorId = mentorId;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
        this.changeReason = changeReason;
    }

    public static MentorCareerHistory create(Long mentorId, CareerLevel previousLevel,
                                             CareerLevel newLevel, String changeReason) {
        return new MentorCareerHistory(mentorId, previousLevel, newLevel, changeReason);
    }
}
