package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.WithdrawalPendingException;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.user.dto.request.AuthorRequest;
import com.example.hot6novelcraft.domain.user.dto.request.CommonUpdateRequest;
import com.example.hot6novelcraft.domain.user.dto.request.LoginUserRequest;
import com.example.hot6novelcraft.domain.user.dto.request.ReaderUpdatedRequest;
import com.example.hot6novelcraft.domain.user.dto.response.*;
import com.example.hot6novelcraft.domain.user.entity.AuthorProfile;
import com.example.hot6novelcraft.domain.user.entity.ReaderProfile;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.ReaderProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j(topic = "AuthService")
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final UserCacheService userCacheService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RedisUtil redisUtil;
    private final AuthorProfileRepository authorProfileRepository;
    private final ReaderProfileRepository readerProfileRepository;

    /**
     * ======== 로그인 및 로그아웃 ========
     * 1. 로그인 - 일반, 소셜, 관리자 공통
     * 2. 내 정보 조회
     * 3. 회원정보 수정 - 공통, 작가, 독자별
     * 4. 비밀번호 변경
     * 5. 회원 탈퇴
     * 6. 로그아웃
     */

    public LoginUserResponse login(LoginUserRequest request) {

        Authentication authentication;

        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_INVALID_EMAIL_OR_PASSWORD);
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();

        if (user.isDeleted()) {

            // 계정 복구 및 초기 파기 요청 임시 토큰
            String recoveryToken = jwtUtil.createRecoveryToken(user.getEmail());
            throw new WithdrawalPendingException(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_CONFLICT, recoveryToken);
        }

        // 정상 일반 유저 토큰 발급
        String accessToken = jwtUtil.createAccessToken(user);
        String refreshToken = jwtUtil.createRefreshToken(user.getEmail());

        long refreshExpiration = jwtUtil.getRefreshExpiration();
        userCacheService.saveRefreshToken(user.getEmail(), refreshToken, refreshExpiration);

        String pureNewRefresh = jwtUtil.substringToken(refreshToken);
        user.updateRefreshToken(pureNewRefresh);

        userRepository.save(user);

        return LoginUserResponse.of(user, accessToken, refreshToken);
    }

    public MyPageResponse getMyPage(UserDetailsImpl userDetails) {

        User user = userRepository.findById(userDetails.getUser().getId())
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        return switch (user.getRole()) {
            case AUTHOR -> {
                AuthorProfile authorProfile = authorProfileRepository.findByUserId(user.getId())
                        .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_AUTHOR_PROFILE));
                yield MyPageResponse.ofAuthor(user, authorProfile);
            }
            case READER -> {
                ReaderProfile readerProfile = readerProfileRepository.findByUserId(user.getId())
                        .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));
                yield MyPageResponse.ofReader(user, readerProfile);
            }
            default -> MyPageResponse.ofDefault(user);
        };
    }

    public CommonUpdateResponse updateUserInfo(CommonUpdateRequest request, UserDetailsImpl userDetails) {

        User user = userRepository.findByIdAndIsDeletedFalse(userDetails.getUser().getId())
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if (request.nickname() != null && !user.getNickname().equals(request.nickname())) {
            if (userRepository.existsByNicknameAndIdNot(request.nickname(), user.getId())) {
                throw new ServiceErrorException(UserExceptionEnum.ERR_NICKNAME_ALREADY_EXISTS);
            }
        }

        String verifiedKeyToDelete = null;

        // 휴대폰 번호 수정
        if (request.phoneNo() != null && !user.getPhoneNo().equals(request.phoneNo())) {

            String cleanPhoneNo = request.phoneNo().replaceAll("-", "");
            String verifiedKey = "SMS:VERIFIED:" + cleanPhoneNo;

            Object isVerified = redisUtil.get(verifiedKey);

            if (isVerified == null || !"TRUE".equals(isVerified.toString())) {

                log.info("[SMS] 인증되지 않은 번호로 접근 시도됨, {} ", cleanPhoneNo);
                throw new ServiceErrorException(UserExceptionEnum.ERR_PHONE_NOT_VERIFIED);
            }

            verifiedKeyToDelete = verifiedKey;
            log.info("[Update] 핸드폰 번호 변경 승인 (Redis 삭제 대기) - user: {}, newPhone: {}", user.getEmail(), cleanPhoneNo);
        }
        user.update(request.nickname(), request.phoneNo());

        // DB update 강제 반영
        userRepository.flush();

        // DB 반영 완료 후, Redis 키 삭제
        if (verifiedKeyToDelete != null) {
            redisUtil.delete(verifiedKeyToDelete);
            log.info("[SMS] Redis 인증 확인 및 삭제 완료, phoneNo: {} ", verifiedKeyToDelete);
        }
        return CommonUpdateResponse.of(user);
    }


    public AuthorUpdateResponse authorUpdateProfile(AuthorRequest request, UserDetailsImpl userDetails) {

        User user = userRepository.findByIdAndIsDeletedFalse(userDetails.getUser().getId())
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if (user.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_AUTHOR_FORBIDDEN);
        }

        AuthorProfile authorProfile = authorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_AUTHOR_PROFILE));

        authorProfile.authorUpdateProfile(
                request.mainGenreToString()
                , request.bio()
                , request.instagramLinks()
                , request.xLinks()
                , request.blogLinks()
                , request.allowMenteeRequest()
        );

        log.info("[작가 프로필 수정] email: {}", user.getEmail());

        return AuthorUpdateResponse.of(authorProfile);

    }

    public ReaderUpdateResponse readerUpdateProfile(ReaderUpdatedRequest request, UserDetailsImpl userDetails) {

        User user = userRepository.findByIdAndIsDeletedFalse(userDetails.getUser().getId())
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        ReaderProfile readerProfile = readerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        readerProfile.readerUpdateProfile(
                request.mainGenreToString(),
                request.readingGoal()
        );

        log.info("[독자 프로필 수정] email: {}", user.getEmail());

        return ReaderUpdateResponse.of(readerProfile);
    }

    public void updatePassword(String oldPassword, String newPassword, UserDetailsImpl userDetails) {

        User user = userRepository.findByIdAndIsDeletedFalse(userDetails.getUser().getId())
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if ("SOCIAL_LOGIN".equals(user.getPassword())) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_SOCIAL_USER_CANNOT_CHANGE_PASSWORD);
        }

        // 현재, 변경 비밀번호 검증
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_PASSWORD_NOT_MATCH);
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_SAME_AS_OLD_PASSWORD);
        }

        user.updatePassword(passwordEncoder.encode(newPassword));

        log.info("[비밀번호 변경] email: {}", user.getEmail());
    }

    public void logout(String accessToken, String email) {
        String token = jwtUtil.substringToken(accessToken);
        userCacheService.deleteRefreshToken(email);

        try {
            long expiration = jwtUtil.getExpiration(token);

            log.warn("===== [디버깅] 추출된 만료 시간 숫자: {} =====", expiration);
            if (expiration > 0) {
                redisUtil.setBlackList(token, "Logout", Duration.ofMillis(expiration));
                log.info("===== [블랙리스트 등록] 사용자가 로그아웃하였습니다. 남은 시간: {}ms =====", expiration);
            }
        } catch (ExpiredJwtException e) {
            log.warn("이미 만료된 토큰입니다. reason: {}", e.getMessage());
        }
    }

    /**
     * ======== 회원 탈퇴 ========
     * 1. 탈퇴 유예 - 탈퇴 직후부터 30일 유예 상태로 변경
     * 2. 회원 복구 - 탈퇴 직후부터 30일 이내 재로그인 (사용자 마음 변함)
     * 3. 즉시 파기 - 30일 이전 유저 요청에 의한 즉시 데이터 파기 (새로운 이력 생성)
     */
    public void withdrawUser(String accessToken, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        // 탈퇴한 유저인지 검증
        if (user.isDeleted()) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_FORBIDDEN);
        }

        // 탈퇴 상태로 변경
        user.withdraw();

        logout(accessToken, email);
        log.info("회원 탈퇴 완료 및 블랙리스트 처리 완료: {}", email);
    }

    public void restoreUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        if (!user.isDeleted()) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_CONFLICT);
        }
        user.restore();
    }

    @Transactional
    public void abandonRecovery(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));
        // 이 유저가 진짜 탈퇴 유예 상태인지 한 번 더 확인
        if (!user.isDeleted()) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_NOT_WITHDRAWAL_PENDING);
        }

        // 30일을 기다리지 않고 지금 즉시 이메일과 닉네임을 UUID로 날림
        user.anonymize();

        log.info("유저 복구 포기: 즉시 비식별화 처리 완료. (기존 이메일: {})", email);
    }
}
