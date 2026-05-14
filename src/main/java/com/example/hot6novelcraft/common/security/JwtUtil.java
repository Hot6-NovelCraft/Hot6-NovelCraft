package com.example.hot6novelcraft.common.security;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j(topic = "JwtUtil")
@Component
public class JwtUtil {

    public static final String BEARER_PREFIX = "Bearer ";

    // 테스트용 배포 전에 바꾸기
    private static final long ACCESS_TOKEN_TIME = 6 * 60 * 60 * 1000L; // 6시간
    private static final long REFRESH_TOKEN_TIME = 24 * 60 * 60 * 1000L; // 24시간
    private static final long TEMP_TOKEN_TIME = 5 * 60 * 1000L; // 임시 토큰 5분
    private static final long SOCIAL_TOKEN_TIME = 10 * 60 * 1000L; // 소셜 가입용 10분
    private static final long RECOVERY_TIME = 10 * 60 * 1000L; // 계정 복구 맟 파기 임시 토큰 10분

    private SecretKey secretKey;
    private JwtParser jwtparser;

    @Value("${jwt.secret.key}")
    private String secretKeyString;

    @PostConstruct
    public void init() {
        byte[] bytes = Decoders.BASE64.decode(secretKeyString);
        this.secretKey = Keys.hmacShaKeyFor(bytes);
        this.jwtparser = Jwts.parser()
                .verifyWith(this.secretKey)
                .build();
    }

    /**
     * ======== 토큰 생성 ========
     * 1. AccessToken
     * 2. RefreshToken : silent RefreshToken 재발행
     * 3. TempToken : 공통 회원가입 -> 작가/독자 회원가입 임시 토큰
     * 4. SocialToken
     * 5. RecoveryToken : 탈퇴 후 계정 복구 및 즉시 파기(30일 이전) 임시 토큰
     * =============================
     */
    public String createAccessToken(User user) {
        Date now = new Date();

        return BEARER_PREFIX + Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("type", "ACCESS")
                .claim("isAdult", user.isAdultVerificationValid())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_TIME))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String createRefreshToken(String email) {
        Date now = new Date();

        return BEARER_PREFIX + Jwts.builder()
                .subject(email)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_TIME))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String createTempToken(String email) {
        Date now = new Date();

        return BEARER_PREFIX + Jwts.builder()
                .subject(email)
                .claim("role", UserRole.TEMP.name())
                .claim("type", "TEMP")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TEMP_TOKEN_TIME))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String createSocialToken(String email, String providerId) {
        Date now = new Date();

        return BEARER_PREFIX + Jwts.builder()
                .subject(email)
                .claim("type", "SOCIAL")
                .claim("providerId", providerId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + SOCIAL_TOKEN_TIME))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String createRecoveryToken(String email) {
        Date now = new Date();

        return BEARER_PREFIX + Jwts.builder()
                .subject(email)
                .claim("type","RECOVERY")
                .issuedAt(now)
                .expiration(new Date(System.currentTimeMillis() + RECOVERY_TIME))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // Bearer 삭제
    public String substringToken(String tokenValue) {
        if(tokenValue != null && tokenValue.startsWith(BEARER_PREFIX)) {
            return tokenValue.substring(7);
        }
        throw new NullPointerException("토큰이 없거나 유효하지 않습니다.");
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank())
            return false;
        try {
            jwtparser.parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("유효하지 않은 토큰입니다. {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("만료된 토큰입니다 {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("지원하지 않는 토큰입니다 {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("토큰이 비어있거나 잘못되었습니다 {}", e.getMessage());
        }
        return false;
    }

    // RefreshToken - Redis 저장 및 RT 생명주기 연장
    public long getRefreshExpiration() {
        return REFRESH_TOKEN_TIME;
    }

    // 블랙리스트 RT 유효시간 계산
    public long getExpiration(String token) {
        try {
            Date expiration = getClaims(token).getExpiration();
            long now = new Date().getTime();
            return (expiration.getTime() - now);
        } catch (ExpiredJwtException e) {
            return 0;
        }
    }

    private Claims getClaims(String token) {
        return jwtparser.parseSignedClaims(token).getPayload();
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    public String extractProviderId(String token) {
        return getClaims(token).get("providerId", String.class);
    }

    /**
     * ======== 토큰 여부 확인 ========
     * 1. RefreshToken
     * 2. TempToken : 공통 회원가입 -> 작가/독자 회원가입 임시 토큰
     * 3. SocialToken
     * 4. RecoveryToken : 탈퇴 후 계정 복구 및 파기(30일 이전) 임시 토큰
     * =============================
     */
    public boolean isRefreshToken(String token) {
        return "REFRESH".equals(getClaims(token).get("type", String.class));
    }

    public boolean isTempToken(String token) {
        return "TEMP".equals(getClaims(token).get("type", String.class));
    }

    public boolean isSocialToken(String token) {
        return "SOCIAL".equals(getClaims(token).get("type", String.class));
    }

    public boolean isRecoveryToken(String token) {
        return "RECOVERY".equals(getClaims(token).get("type", String.class));
    }
}
