package com.example.hot6novelcraft.domain.user.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.UUID;

@Getter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users"
        , indexes = {
        @Index(name = "idx_user_role_deleted", columnList = "is_deleted, role") // 역할별 회원 조회
        , @Index(name = "idx_user_created_at", columnList = "created_at") // 신규 회원 조회
})
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(nullable = true, length = 20)
    private String phoneNo;

    @Column(nullable = true)
    private LocalDate birthday;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    // 성인 인증 여부
    @Column(nullable = false)
    private boolean isAdultVerified = false;

    private String refreshToken;

    private boolean isDeleted;

    private LocalDateTime deletedAt;

    private LocalDateTime updatedAt;

    private LocalDateTime anonymizedAt;

    private LocalDateTime adultVerifiedAt;

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PreRemove
    protected void preRemove() {
        deletedAt = LocalDateTime.now();
    }

    public static User register(String email, String password, String nickname, String phoneNo, LocalDate birthday, UserRole role) {
        return User.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .phoneNo(phoneNo)
                .birthday(birthday)
                .role(role)
                .build();
    }

    // 관리자 전용 메서드
    public static User registerAdmin(String email, String password, String phoneNo, UserRole role) {
        return User.register(
                email,
                password,
                "ADMIN_" + email,
                phoneNo,
                null,
                UserRole.PENDING_ADMIN
        );
    }

    // 소셜 로그인 빌드
    public static User socialUser(String email, String nickname, UserRole role) {
        return User.builder()
                .email(email)
                .nickname(nickname)
                .role(role)
                .password("SOCIAL_LOGIN")
                .birthday(null)
                .phoneNo(null)
                .build();
    }

    // 소셜 회원가입 추가 정보 업데이트
    public void updateForSocialSignup(String nickname, String phoneNo, LocalDate birthday) {
        this.nickname = nickname;
        this.phoneNo = phoneNo;
        this.birthday = birthday;
    }

    // 회원정보 수정
    public void update(String nickname, String phoneNo) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (phoneNo != null) {
            this.phoneNo = phoneNo;
        }
    }

    // 비밀번호 수정
    public void updatePassword(String password) {
        this.password = password;
    }

    // 회원 탈퇴
    public void withdraw() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.refreshToken = null;
    }

    // 회원 탈퇴 30일 경과 후 비식별화 : 스케쥴러가 호출, unique 제약조건 풀고 개인정보 파기
    public void anonymize() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        this.email = "deleted_" + uuid + "@anonymous.com";
        this.nickname = "알수없음_" + uuid;

        this.password = "DELETED";
        this.phoneNo = null;
        this.birthday = null;

        // 비식별화 완료 시간
        this.anonymizedAt = LocalDateTime.now();
    }

    // 30일 이내 계정 복구
    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;

        this.anonymizedAt = null;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void changeRole(UserRole role) {
        this.role = role;
    }

    // 단순 나이 계산
    public boolean isAdult() {
        if (this.birthday == null) {
            return false;
        }
        int age = Period.between(this.birthday, LocalDate.now()).getYears();
        return age >= 19;
    }

    // 성인 인증
    public void verifyAdult() {
        this.isAdultVerified = true;
        this.adultVerifiedAt = LocalDateTime.now();
    }

    // 성인 인증 완료 (1년 유효기간)
    public boolean isAdultVerificationValid() {
        if (!isAdultVerified || adultVerifiedAt == null) {
            return false;
        }
        return adultVerifiedAt.plusYears(1).isAfter(LocalDateTime.now());
    }
}
