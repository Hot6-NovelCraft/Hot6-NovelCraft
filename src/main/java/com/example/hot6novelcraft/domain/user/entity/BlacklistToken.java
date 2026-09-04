package com.example.hot6novelcraft.domain.user.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Table(name = "blacklist_tokens"
        , indexes = @Index(name = "idx_blacklist_token", columnList = "token")
)
public class BlacklistToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JWT access token
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private String reason;

    // 스케쥴러용 - 토큰 만료 시간
    @Column(nullable = false)
    private LocalDateTime expiredAt;

    public static BlacklistToken of(
            String token
            , String reason
            , LocalDateTime expiredAt) {
        return BlacklistToken.builder()
                .token(token)
                .reason(reason)
                .expiredAt(expiredAt)
                .build();
    }
}
