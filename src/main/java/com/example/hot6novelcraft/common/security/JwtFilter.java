package com.example.hot6novelcraft.common.security;

import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.service.UserCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j(topic = "JwtFilter")
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final UserDetailsService userDetailsService;
    private final UserCacheService userCacheService;


    // 임시 JWT로만 접근 가능한 URL - 회원가입 시에만 사용
    private static final List<String> TEMP_TOKEN_ALLOWED_USERS
            = List.of("/api/auth/signup/reader", "/api/auth/signup/author");

    private static final List<String> SOCIAL_TOKEN_ALLOWED_URLS
            = List.of("/api/auth/social/signup/**");

    // 비로그인 GET 허용 패턴 (소설 상세, 에피소드 목록 등)
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> PUBLIC_GET_PATTERNS = List.of(
            "/api/novels/*",
            "/api/novels/*/episodes",
            "/api/novels/*/episodes/*",
            "/api/episodes/*/comments",
            "/api/novels/ranking/**",
            "/api/v2/episodes/*"
    );

    // 토큰 없이 통과 가능한 URL
    private static final List<String> PUBLIC_URLS
            = List.of(
            "/api/auth/signup"
            , "/api/auth/signup/admin"
            , "/api/auth/login"
            , "/api/auth/email/check"
            , "/api/auth/nickname/check"
            , "/api/auth/phone/send"
            , "/api/auth/phone/verify"
            , "/api/auth/users/restore"
            , "/api/auth/users/abandon-recovery"
            , "/api/webhooks/portone"
            , "/favicon.ico"
            , "/login"          // 구글이 에러 시 여기로 리다이렉트
            , "/login?error"
            , "/api/novels/ranking"
            , "/actuator/prometheus"
            , "/api/novels/new"             // 신작 목록
            , "/api/search/keywords/popular"   // 인기 검색어
            , "/api/search/tags/popular"
            , "/api/v2/novels");

    // 토큰이 있으면 인증하고, 없어도 통과가능한 URL (선택적 인증)
    private static final List<String> OPTIONAL_AUTH_URLS = List.of(
            "/api/search/v2/novels"
            , "/api/search/v2/tags"
            , "/api/search/v2/authors"
            , "/api/ai/recommendation"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/oauth2/authorize")
                || path.startsWith("/api/auth/login/oauth2")
                || path.startsWith("/login/oauth2")
                || path.equals("/ws-chat") || path.startsWith("/ws-chat/")
                || path.equals("/login")
                || path.equals("/favicon.ico")
                || path.equals("/login.html")
                || path.equals("/index.html")
                || path.equals("/signup.html")
                || path.equals("/error")
                || path.endsWith(".html")
                || path.endsWith(".js")
                || path.endsWith(".css")
                || path.equals("/common.js")
                || path.startsWith("/static/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.equals("/.well-known/appspecific/com.chrome.devtools.json")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURL = request.getRequestURI();

        // 인증 불필요 경로 바로 통과
        if (PUBLIC_URLS.stream().anyMatch(requestURL::equals)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (TEMP_TOKEN_ALLOWED_USERS.contains(requestURL) ||
                requestURL.startsWith("/api/auth/social/signup")) {

            // TempToken이 유효한지 껍데기만 검사
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith(JwtUtil.BEARER_PREFIX)) {
                String token = jwtUtil.substringToken(authHeader);

                // 토큰 유효성 검사
                if (!jwtUtil.validateToken(token)) {
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "토큰이 만료되었거나 유효하지 않습니다.");
                    return;
                }

                if (!setAuthentication(response, token, requestURL)) {
                    return;
                }
            }

            // DB 조회 없이 컨트롤러(서비스)로 패스!
            filterChain.doFilter(request, response);
            return;
        }

        // JWT 토큰 유무 검사
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith(JwtUtil.BEARER_PREFIX)) {

            // 비로그인 허용 GET 패턴이면 통과
            if ("GET".equalsIgnoreCase(request.getMethod()) &&
                    PUBLIC_GET_PATTERNS.stream().anyMatch(p -> PATH_MATCHER.match(p, requestURL))) {
                filterChain.doFilter(request, response);
                return;
            }

            // 선택적 인증 URL (토큰 없어도 통과, 있으면 인증)
            if (OPTIONAL_AUTH_URLS.contains(requestURL)) {
                filterChain.doFilter(request, response);
                return;
            }

            log.warn("JWT 토큰이 없거나 형식이 잘못되었습니다. URL : {}", requestURL);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT 토큰이 없거나 형식이 잘못되었습니다.");
            return;
        }

        // AccessToken 전달 및 유효성 검사
        String accessToken = jwtUtil.substringToken(authorizationHeader);

        if (jwtUtil.validateToken(accessToken)) {

            // Redis 블랙리스트 검사
            try {
                // Redis 정상 동작
                if (redisUtil.isBlackList(accessToken)) {
                    log.warn("블랙리스트에 등록된 토큰입니다.");
                    response.setCharacterEncoding("UTF-8");
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "토큰이 유효하지 않습니다.");
                    return;
                }
            } catch (Exception e) {
                // Redis 장애 상황(RedisConnectionFailureException, RedisSystemException 등
                // Lettuce/Redisson이 던지는 구체 타입에 상관없이) — 블랙리스트 확인 자체가 불가능한 상태로 통일 처리.
                // 예외 타입별로 분기를 나누지 않는 이유: Fail-Open/Fail-Closed는 "예외 종류"가 아니라
                // "API 위험도(isSafeApi)"로 결정하는 게 이 필터의 설계 의도이기 때문.
                log.error("[Redis 장애] 블랙리스트 확인 불가, URL: {}", requestURL, e);

                if (isSafeApi(request)) {
                    // 읽기 전용 화이트리스트 API: 가용성 우선(Fail-Open).
                    // 여기서 응답을 보내지 않고 return도 하지 않는다.
                    // -> 이 catch 블록을 그냥 빠져나가서 아래 setAuthentication -> filterChain.doFilter로
                    //    이어지고, 컨트롤러 -> 서비스 계층의 DB Fallback 로직이 실제로 200을 만들어낸다.
                    log.warn("[Redis 장애] 가용성을 위해 읽기 전용 API 접근을 허용합니다(블랙리스트 미확인). URL: {}", requestURL);
                } else {
                    // 상태 변경/민감 API: 보안 우선(Fail-Closed). 블랙리스트를 확인 못 한 채로
                    // 인증을 통과시키는 건 탈취된 토큰이 재사용될 위험이 있으므로 무조건 차단.
                    log.error("[Redis 장애] 데이터 보호를 위해 해당 API 접근을 기본 차단합니다. URL: {}", requestURL);
                    sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "현재 서버 불안정으로 해당 기능을 사용할 수 없습니다.");
                    return;
                }
            }

            // 인증 실패 시 return
            // 주의: 위에서 isSafeApi=true로 흘러온 경우, 블랙리스트가 미확인 상태임에도
            // 여기서 정상적으로 인증(SecurityContext 세팅)을 진행함 -> 이건 의도된 트레이드오프.
            // (조회 전용 API에 한해 "블랙리스트 미확인 상태의 유효한 토큰"을 신뢰하는 것 = Fail-Open 정책 그 자체)
            if (!setAuthentication(response, accessToken, requestURL)) {
                return;
            }

            // AccessToken 인증인가 필터 종료
            filterChain.doFilter(request, response);
            return;

        }

        log.info("[Silent Refresh] AccessToken 만료 감지. URL: {}", requestURL);

        String refreshTokenHeader = request.getHeader("Refresh-Token");

        if (refreshTokenHeader == null || !refreshTokenHeader.startsWith(JwtUtil.BEARER_PREFIX)) {

            log.warn("[Silent Refresh] Refresh-Token 헤더가 없습니다.");
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "AccessToken이 만료되었습니다. Refresh-Token을 보내주세요.");
            return;
        }
        String refreshToken = jwtUtil.substringToken(refreshTokenHeader);

        if (!jwtUtil.validateToken(refreshToken)) {

            log.warn("[Silent Refresh] RefreshToken이 만료되었습니다");
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "다시 로그인해주세요.");
            return;
        }

        String email = jwtUtil.extractEmail(refreshToken);
        String savedRefreshToken = userCacheService.getRefreshToken(email);

        if (savedRefreshToken == null || !savedRefreshToken.equals(refreshTokenHeader)) {

            log.warn("[Silent Refresh] Redis에 RefreshToken과 불일치. email: {}", email);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "RefreshToken이 유효하지 않습니다. 다시 로그인해주세요.");
            return;
        }

        UserDetailsImpl userDetailsImpl = (UserDetailsImpl) userDetailsService.loadUserByUsername(email);
        String newAccessToken = jwtUtil.createAccessToken(userDetailsImpl.getUser());

        response.setHeader("Authorization", newAccessToken);
        response.setHeader("Access-Control-Expose-Headers", "Authorization");
        log.info("[Silent Refresh] 새로운 AccessToken 발급 완료, email: {}", email);

        String pureNewAccessToken = jwtUtil.substringToken(newAccessToken);
        if (!setAuthentication(response, pureNewAccessToken, requestURL)) {
            return;
        }

        // 만료 및 재발행 Silent Refresh 필터 종료
        filterChain.doFilter(request, response);
    }

    // 임시 토큰 인가 확인
    private boolean setAuthentication(HttpServletResponse response, String token, String requestURL) {

        try {
            String email = jwtUtil.extractEmail(token);
            AntPathMatcher pathMatcher = new AntPathMatcher();

            // 토큰 타입 확인
            boolean isSocialToken = jwtUtil.isSocialToken(token);
            boolean isTempToken = jwtUtil.isTempToken(token);

            // [인가 검증] 소셜/임시 토큰이지만 엉뚱한 주소인지 확인
            if (isSocialToken && SOCIAL_TOKEN_ALLOWED_URLS.stream()
                    .noneMatch(patten -> pathMatcher.match(patten, requestURL))) {

                log.warn("소셜 토큰으로 허용되지 않은 URL 접근, URL: {}", requestURL);
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "소셜 회원가입을 먼저 완료해주세요.");
                return false;
            }

            if (isTempToken && !TEMP_TOKEN_ALLOWED_USERS.contains(requestURL)) {

                log.warn("임시 토큰으로 허용되지 않은 URL 접근, URL: {}", requestURL);
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "추가 정보 회원가입이 필요합니다.");
                return false;
            }

            // 가입 중인 유저 (Temp)는 DB 조회 없이 임시 객체 생성
            UserDetails userDetails;

            if (isSocialToken || isTempToken) {
                // DB로 안가고 바로 임시 UserDetailsImpl 생성 -> 컨트롤러에 이메일 전달
                userDetails = UserDetailsImpl.fromTemp(email, UserRole.TEMP);
            } else {
                // 일반 액세스토큰 일 때만 DB 조회
                userDetails = userDetailsService.loadUserByUsername(email);
            }

            // 시큐리티 컨텍스트에 저장
            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
            return true;

        } catch (Exception e) {

            log.error("인증 처리 중 오류 발생: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "인증 처리 중 서버 오류가 발생했습니다.");
            return false;
        }
    }

    // 오류 메시지 공통 메서드
    private void sendErrorResponse(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(String.format("{\"message\": \"%s\"}", message));
        } catch (IOException e) {
            log.error("에러 응답 전송 실패: {}", e.getMessage());
        }
    }

    // 차단할 중요 API 리스트 판별 메서드
    private boolean isSafeApi(HttpServletRequest request) {
        String url = request.getRequestURI();
        String method = request.getMethod();
        AntPathMatcher pathMatcher = new AntPathMatcher();

        // 상태를 변경하는 요청 (POST, PUT, PATCH, DELETE)는 차단
        if (!method.equalsIgnoreCase("GET")) {
            return false;
        }

        return pathMatcher.match("/api/novels/**", url) ||
                pathMatcher.match("/api/search/**", url) ||
                pathMatcher.match("/api/episodes/**", url) ||
                pathMatcher.match("/api/reviews/**", url) ||
                pathMatcher.match("/api/comments/**", url) ||
                pathMatcher.match("/api/categories/**", url);
    }
}
