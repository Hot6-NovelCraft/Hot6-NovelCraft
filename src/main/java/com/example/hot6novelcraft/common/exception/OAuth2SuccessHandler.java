package com.example.hot6novelcraft.common.exception;

import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import com.example.hot6novelcraft.domain.user.service.UserCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j(topic = "OAuth2SuccessHandler")
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserCacheService userCacheService;
    private final UserRepository userRepository;

    // 프론트 URL 주입
    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request
            , HttpServletResponse response
            , Authentication authentication
    ) throws IOException {

        // GoogleOAuth2UserService에서 반환한 UserDetailsImpl 꺼내기
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();

        String redirectUrl;

        // 신규 회원 : SocialToken 받음 -> 공통 가입 페이지로 이동
        if (user.getRole() == UserRole.TEMP) {
            String socialToken = jwtUtil.createSocialToken(user.getEmail());
            String pureToken = socialToken.replace("Bearer ", "");

            log.info("[OAuth2 성공] 신규 소셜 유저, 추가정보 입력 필요. email: {}", user.getEmail());

            getRedirectStrategy().sendRedirect(request, response, frontendUrl
                    + "/signup.html?socialToken="
                    + URLEncoder.encode(pureToken, StandardCharsets.UTF_8));
            return;

        }

            // 기존 유저 : 로그인 AccessToken + RefreshToken 발급
            String accessToken = jwtUtil.createAccessToken(user);
            String refreshToken = jwtUtil.createRefreshToken(user.getEmail());

            // Redis에 refreshToken 저장
            long refreshExpiration = jwtUtil.getRefreshExpiration();
            userCacheService.saveRefreshToken(user.getEmail(), refreshToken, refreshExpiration);

            // DB refreshToken 업데이트 (중복 제거, 한 번만)
            userRepository.findByEmail(user.getEmail())
                    .ifPresent(u -> u.updateRefreshToken(jwtUtil.substringToken(refreshToken)));

            log.info("[OAuth2 성공] 기존 소셜 유저 로그인 완료. email: {}", user.getEmail());

            redirectUrl = UriComponentsBuilder
                .fromUriString(frontendUrl + "/index.html")
                .queryParam("accessToken", URLEncoder.encode(accessToken, StandardCharsets.UTF_8))
                .queryParam("refreshToken", URLEncoder.encode(refreshToken, StandardCharsets.UTF_8))
                .build().toUriString();

            // 프론트로 리다이렉트
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        }

    }
