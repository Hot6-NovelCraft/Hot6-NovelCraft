package com.example.hot6novelcraft.domain.mentor.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@AllArgsConstructor
@Table(name = "mentors"
        , indexes = {

        @Index(name = "idx_mentor_status", columnList = "status") // 승인 상태별 멘토 조회
        , @Index(name = "idx_mentor_created_at", columnList = "created_at") // 신규 멘토 조회
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mentor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareerLevel careerLevel;

    private String mainGenres;

    private String specialFields;

    private String mentoringStyle;

    @Column(length = 500)
    private String bio;

    @Column(length = 500)
    private String awardsCareer;

    @Column(nullable = false)
    private Integer maxMentees;

    @Column(nullable = false)
    private Boolean allowInstant;

    @Column(length = 500)
    private String preferredMenteeDesc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MentorStatus status;

    @Column(length = 500)
    private String rejectReason;

    @Version
    private long version;

    public void approve() {

        if(this.status != MentorStatus.PENDING) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_ALREADY_PROCESSED);
        }
        this.status = MentorStatus.APPROVED;
        this.rejectReason = null;
    }

    public void reject(String rejectReason) {

        if(this.status != MentorStatus.PENDING) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_ALREADY_PROCESSED);
        }
        this.status = MentorStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    public void decreaseSlot() {
        if (this.maxMentees <= 0) {
            throw new ServiceErrorException(MentorExceptionEnum.MENTOR_MAX_MENTEES_EXCEEDED);
        }
        this.maxMentees--;
    }

    public void increaseSlot() {
        this.maxMentees++;
    }

    private Mentor(Long userId, CareerLevel careerLevel, String mainGenres, String specialFields,
                   String mentoringStyle, String bio, String awardsCareer,
                   Integer maxMentees, Boolean allowInstant, String preferredMenteeDesc,
                   MentorStatus status) {
        this.userId = userId;
        this.careerLevel = careerLevel;
        this.mainGenres = mainGenres;
        this.specialFields = specialFields;
        this.mentoringStyle = mentoringStyle;
        this.bio = bio;
        this.awardsCareer = awardsCareer;
        this.maxMentees = maxMentees;
        this.allowInstant = allowInstant;
        this.preferredMenteeDesc = preferredMenteeDesc;
        this.status = status;
    }

    public static Mentor create(Long userId, CareerLevel careerLevel, String mainGenres,
                                String specialFields, String mentoringStyle, String bio,
                                String awardsCareer, Integer maxMentees, Boolean allowInstant,
                                String preferredMenteeDesc, MentorStatus initialStatus) {
        return new Mentor(userId, careerLevel, mainGenres, specialFields, mentoringStyle,
                bio, awardsCareer, maxMentees, allowInstant, preferredMenteeDesc, initialStatus);
    }

    public void update(String introduction, String mainGenres, String specialFields,
                       String mentoringStyle, String awardsCareer, Integer maxMentees,
                       Boolean allowInstant, String preferredMenteeDesc) {
        if (introduction != null && !introduction.isBlank()) this.bio = introduction;
        if (mainGenres != null) this.mainGenres = mainGenres;
        if (specialFields != null) this.specialFields = specialFields;
        if (mentoringStyle != null) this.mentoringStyle = mentoringStyle;
        if (awardsCareer != null) this.awardsCareer = awardsCareer;
        if (maxMentees != null) this.maxMentees = maxMentees;
        if (allowInstant != null) this.allowInstant = allowInstant;
        if (preferredMenteeDesc != null) this.preferredMenteeDesc = preferredMenteeDesc;
    }

    public void upgradeCareerLevel(CareerLevel newLevel) {
        this.careerLevel = newLevel;
    }
}