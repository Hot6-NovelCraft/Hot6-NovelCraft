package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.OAuth2ExceptionEnum;
import com.example.hot6novelcraft.domain.user.entity.SocialAuth;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.ProviderSns;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.SocialAuthRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService; // ✅ import 추가
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;        // ✅ import 추가
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;        // ✅ import 추가
import org.springframework.security.oauth2.core.OAuth2Error;                          // ✅ import 추가
import org.springframework.security.oauth2.core.user.OAuth2User;                      // ✅ import 추가
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j(topic = "CustomOAuth2UserService")
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final SocialAuthRepository socialAuthRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // 부모 클래스(DefaultOAuth2UserService)가 구글 API를 호출해 사용자 정보를 가져옴
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // registrationId로 플랫폼 구분 (현재 구글만, 추후 kakao/naver 추가 시 여기만 확장)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        ProviderSns provider = ProviderSns.from(registrationId);

        // 플렛폼 별 정보 추출 (email, ProviderId)
        String email = extractEmail(oAuth2User, registrationId);
        String providerId = extractProviderId(oAuth2User, registrationId);

        // 이메일이 없으면 구글 계정 문제 (이메일 공개 비허용 설정 등 예외 상황)
        if (email == null || email.isBlank()) {
            log.error("[소셜 로그인] 구글에서 이메일 정보를 받지 못했습니다.");
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found"), "구글 계정에서 이메일을 가져올 수 없습니다.");
        }
        log.info("[소셜 로그인] 구글 사용자 정보 수신, email: {}", email);

        boolean isExisting = socialAuthRepository
                .existsByProviderAndProviderId(provider, providerId);

        // DB에서 이메일로 기존 유저 조회
        User user;

        if (!isExisting) {

            java.util.Optional<User> userByEmail = userRepository.findByEmail(email);

            if (userByEmail.isPresent()) {
                User existingUser = userByEmail.get();
                java.util.Optional<SocialAuth> existingSocialAuth =
                        socialAuthRepository.findByUserId(existingUser.getId());

                if (existingSocialAuth.isPresent() && existingSocialAuth.get().getProvider() == provider) {
                    // 동일 provider이지만 providerId가 null이었던 기존 유저 → providerId 갱신 후 로그인 처리
                    existingSocialAuth.get().updateProviderId(providerId);
                    log.info("[소셜 로그인] 동일 provider 기존 유저 providerId 갱신. email: {}", email);
                    user = existingUser;
                } else {
                    // 다른 provider 또는 일반 이메일로 가입된 이메일 → SuccessHandler에서 충돌 처리
                    log.warn("[소셜 로그인] 이미 다른 방식으로 등록된 이메일. email: {}", email);
                    user = User.socialUser(email, "SOCIAL_CONFLICT_" + System.currentTimeMillis(), UserRole.TEMP);
                }
            } else {
                // 완전 신규 유저 → TEMP로 signup.html 이동
                user = User.socialUser(
                        email,
                        "SOCIAL_" + System.currentTimeMillis(),
                        UserRole.TEMP
                );
                log.info("[소셜 로그인] 신규 유저 감지 (임시 정보), email: {}", email);
            }

        } else {

            // 기존 유저 : SocialAuth로 유저 조회
            SocialAuth socialAuth = socialAuthRepository
                    .findByProviderAndProviderId(provider, providerId)
                    .orElseThrow(() -> new ServiceErrorException(OAuth2ExceptionEnum.ERR_NOT_FOUND_SOCIAL_ACCOUNT));

            user = userRepository.findById(socialAuth.getUserId())
                    .orElseThrow(() -> new ServiceErrorException(OAuth2ExceptionEnum.ERR_NOT_FOUND_USER));

            log.info("[소셜 로그인] 기존 소셜 유저 로그인, email: {}", email);
        }
        // UserDetailsImpl 로 반환해서 SuccessHandler에서 사용
        return new UserDetailsImpl(user, oAuth2User.getAttributes());
    }

    // 플랫폼별 이메일 추출
    private String extractEmail(OAuth2User oAuth2User, String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> oAuth2User.getAttribute("email");
            case "kakao" -> {
                Object idObj = oAuth2User.getAttributes().get("id");
                Long kakaoId = idObj instanceof Long ? (Long) idObj : Long.valueOf(String.valueOf(idObj));
                Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
                String kakaoEmail = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
                yield kakaoEmail != null ? kakaoEmail : kakaoId + "@kakao.com"; //  이메일이 없으면 임시 생성
            }
            case "naver" -> {
                Map<String, Object> naverResponse = oAuth2User.getAttribute("response");
                String naverEmail = naverResponse != null ? (String) naverResponse.get("email") : null;
                yield naverEmail != null ? naverEmail : naverResponse.get("id") + "@naver.com";
            }
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"), "지원하지 않는 소셜 플랫폼입니다: " + registrationId);
        };
    }

    // 플랫폼별 고유 ID 추출
    private String extractProviderId(OAuth2User oAuth2User, String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> oAuth2User.getAttribute("sub");
            case "kakao" -> String.valueOf(oAuth2User.getAttributes().get("id"));
            case "naver" -> {
                Map<String, Object> naverResponse = oAuth2User.getAttribute("response");
                yield naverResponse != null ? (String) naverResponse.get("id") : null;
            }
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"), "지원하지 않는 소셜 플랫폼입니다: " + registrationId);
        };
    }
}