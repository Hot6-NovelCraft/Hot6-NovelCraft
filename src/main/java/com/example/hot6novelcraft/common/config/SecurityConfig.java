package com.example.hot6novelcraft.common.config;

import com.example.hot6novelcraft.common.exception.OAuth2SuccessHandler;
import com.example.hot6novelcraft.common.security.JwtFilter;
import com.example.hot6novelcraft.domain.user.service.CustomOAuth2UserService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"message\": \"인증이 필요합니다.\"}");
                        })
                )
                // 소셜 로그인 설정
                .oauth2Login(oauth2 ->oauth2
                        // 구글 로그인 시작
                        .authorizationEndpoint(endpoint
                                -> endpoint.baseUri("/oauth2/authorize"))
                        // 구글 콜백 URL
                        .redirectionEndpoint(endpoint
                                -> endpoint.baseUri("/api/auth/login/oauth2/**"))
                        // 구글에서 사용자 정보 받아서 처리
                        .userInfoEndpoint(userInfo
                                -> userInfo.userService(customOAuth2UserService))
                        // 성공 -> 토큰 발급 + 프론트 리다이렉트
                        .successHandler(oAuth2SuccessHandler)
                        // 실패 -> 리다이렉트
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"message\": \"소셜 로그인에 실패했습니다.\"}");
                        })
                )

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/api/auth/login"
                                , "/api/auth/signup"
                                , "/api/auth/signup/admin"
                                , "/api/auth/signup/reader"
                                , "/api/auth/signup/author"
                                ,"/api/auth/social/signup/**"
                                , "/api/auth/email/check"
                                , "/api/auth/nickname/check"
                                , "/api/auth/phone/send"
                                , "/api/auth/phone/verify"
                                , "/api/auth/users/restore"
                                , "/api/auth/users/abandon-recovery"
                                , "/api/webhooks/portone"
                                , "/api/auth/social/signup/**"
                                , "/api/auth/login/oauth2/**"
                                , "/api/novels/ranking/**"
                                , "/oauth2/authorize/**"
                                , "/error"
                                , "/*.html"
                                , "/static/**"
                                , "/css/**"
                                , "/js/**"
                                , "/images/**"
                                , "/api/dummy/**"  // 에피소드 더미용
                                // WebSocket (STOMP + SockJS)
                                , "/ws-chat/**"
                                , "/ws-chat/**"
                                , "/actuator/**"
                                , "/api/ai/recommendation"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/calendars/**").hasAnyAuthority("READER", "AUTHOR")
                        .requestMatchers("/api/ai-support/**").authenticated()
                        .anyRequest().authenticated()
                )
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager (AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // SecurityConfig.java 에 이 Bean 추가
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers(
                        "/*.html",
                        "/**.html",
                        "/*.js",
                        "/**.js",
                        "/*.css",
                        "/**.css",
                        "/static/**",
                        "/images/**",
                        "/favicon.ico",
                        "/.well-known/**"
                );
    }
}