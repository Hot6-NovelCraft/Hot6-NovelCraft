package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.admin.service.AdminCacheService;
import com.example.hot6novelcraft.domain.user.dto.request.*;
import com.example.hot6novelcraft.domain.user.dto.response.AdminSignupResponse;
import com.example.hot6novelcraft.domain.user.dto.response.SignupResponse;
import com.example.hot6novelcraft.domain.user.dto.response.SocialSignupResponse;
import com.example.hot6novelcraft.domain.user.entity.AuthorProfile;
import com.example.hot6novelcraft.domain.user.entity.ReaderProfile;
import com.example.hot6novelcraft.domain.user.entity.SocialAuth;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.ProviderSns;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.ReaderProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.SocialAuthRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j(topic = "SignupService")
@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final AuthorProfileRepository authorProfileRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final SocialAuthRepository socialAuthRepository;
    private final RedisUtil redisUtil;
    private final AdminCacheService adminCacheService;
    private final ObjectMapper objectMapper;

    /** ======== 중복 확인 ========
    1. 이메일 중복 확인
    2. 닉네임 중복 확인
    탈퇴 후 재가입시도 시 확인 및 30일 이내 탈퇴자가 있을 때 사용
    ============================= */
    public void checkEmail(String email) {

        Optional<User> optionalUser = userRepository.findByEmail(email);

        // 탈퇴 유예 상태(30일 이내)인 경우 -> 복구 유도 에러
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();

            // 이메일이 겹쳐서 가입이 안 되는 경우에도, 복구하시겠습니까? 로 유도할 수 있음
            if (user.isDeleted()) {
                throw new ServiceErrorException(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_CONFLICT);
            }
            // 멀쩡히 활동 중인 계정이라면? 일반적인 이메일 중복 에러
            throw new ServiceErrorException(UserExceptionEnum.ERR_EMAIL_ALREADY_EXISTS);
        }
    }

    public void checkNickname(String nickname) {
        Optional<User> optionalUser = userRepository.findByNickname(nickname);

        if (optionalUser.isPresent()) {
            User existingUser = optionalUser.get();

            // 닉네임 주인이 탈퇴 유예(30일) 상태
            if (existingUser.isDeleted()) {

                // 닉네임이 겹쳐서 가입이 안 되는 경우에도, 복구하시겠습니까? 로 유도할 수 있음
                throw new ServiceErrorException(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_CONFLICT);
            }
            // 멀쩡히 활동 중인 계정이라면? 일반적인 닉네임 중복 에러
            throw new ServiceErrorException(UserExceptionEnum.ERR_NICKNAME_ALREADY_EXISTS);
        }
    }

    /** ======== 회원 가입 ========
    1. 공통 회원가입
        - 독자/작가 추가 정보 기입까지 완료 후, DB 저장 및 임시 JWT 발급으로 보안 설정
        - SMS 전송 및 인증
    2. 독자 회원가입 - 임시 JWT로만 접근 가능, 하단 공통 메소드로 소셜/일반 분리
    3. 작가 회원가입 - 임시 JWT로만 접근 가능, 하단 공통 메소드로 소셜/일반 분리
    ============================= */
    @Transactional
    public String commonSignup(CommonSignupRequest request) {

        // SMS 인증
        consumePhoneVerification(request.tempToken(), request.phoneNo());

        // 이메일 및 닉네임 중복 확인
        checkEmail(request.email());
        checkNickname(request.nickname());

        String encoderPassword = passwordEncoder.encode(request.password());

        // Redis 임시 토큰 Temp에 담을 정보 (유효시간 10분)
        TempSignupRequest tempRequest = new TempSignupRequest(
                request.email(),
                encoderPassword,
                request.nickname(),
                request.phoneNo(),
                request.birthDay()
        );
        String redisKey = "TEMP_SIGNUP:" + request.email();
        redisUtil.set(redisKey, tempRequest, 10);

        log.info("공통 회원가입 임시 보관 완료 - email: {}", request.email());

        // 임시 JWT 유저 저장 (독자/작가 추가 가입 전 임시 상태)
        return jwtUtil.createTempToken(request.email());
    }

    @Transactional
    public SignupResponse readerSignup(ReaderSignupRequest request, String email) {

        // 소셜 회원가입 : 먼저 소셜 가입이 있는지 확인과 동시에 삭제
        String socialKey = "TEMP_SOCIAL_SIGNUP:" + email;
        Object socialData = redisUtil.getAndDelete(socialKey);
        TempSocialSignupRequest tempSocialSignupRequest = objectMapper.convertValue(socialData, TempSocialSignupRequest.class);

        // 동시 요청이 들어왔을 때, 첫 번째 요청만 데이터를 받고 두 번째 요청은 null 로 반환
        if (tempSocialSignupRequest != null) {
            return processSocialReaderSignup(request, email, tempSocialSignupRequest, socialKey);
        }

        // 일반 회원가입 : 먼저 소셜 가입이 있는지 확인과 동시에 삭제
        String normalKey = "TEMP_SIGNUP:" + email;
        Object normalData = redisUtil.getAndDelete(normalKey);
        TempSignupRequest tempRequest = objectMapper.convertValue(normalData, TempSignupRequest.class);

        if (tempRequest != null) {
            return processNormalReaderSignup(request, email, tempRequest, normalKey);
        }

        // 둘 다 없으면 예외 처리
        throw new ServiceErrorException(UserExceptionEnum.ERR_INVALID_TOKEN);
    }

    @Transactional
    public SignupResponse authorSignup(AuthorRequest request, String email) {

        String socialKey = "TEMP_SOCIAL_SIGNUP:" + email;
        Object socialData = redisUtil.getAndDelete(socialKey);
        TempSocialSignupRequest tempSocialSignupRequest = objectMapper.convertValue(socialData, TempSocialSignupRequest.class);

        if(tempSocialSignupRequest != null) {
            return processSocialAuthorSignup(request, email, tempSocialSignupRequest, socialKey);
        }

        String normalKey = "TEMP_SIGNUP:" + email;
        Object normalData = redisUtil.getAndDelete(normalKey);
        TempSignupRequest tempRequest = objectMapper.convertValue(normalData, TempSignupRequest.class);

        if(tempRequest != null) {
            return processNormalAuthorSignup(request, email, tempRequest, normalKey);
        }

        throw new ServiceErrorException(UserExceptionEnum.ERR_INVALID_TOKEN);
    }

    /** ======== 소셜 회원 가입 ========
     1. 소셜 공통 회원 가입 - 번호 인증 필요, 닉네임, 생일 입력
     2. 소셜 독자 회원 가입 - private
     3. 소셜 작가 회원 가입 - private
     ============================= */
    @Transactional
    public SocialSignupResponse socialCommonSignup(SocialSignupRequest request, String email, String providerId, ProviderSns providerSns) {

        consumePhoneVerification(request.tempToken(), request.phoneNo());
        checkNickname(request.nickname());

        // 소셜 유저 검증 - 탈퇴 유예 기간 유저인지 체크 및 비밀번호는 SOCIAL LOGIN으로 고정
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if(optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.isDeleted()) {
                throw new ServiceErrorException(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_CONFLICT);
            }
            throw new ServiceErrorException(UserExceptionEnum.ERR_ALREADY_COMPLETED_SIGNUP);
        }

        // 1단계 정보 DTP에 담기
        TempSocialSignupRequest tempSocialSignupRequest = new TempSocialSignupRequest(
                providerId
                , providerSns
                , request.nickname()
                , request.phoneNo()
                , request.birthDay()
        );

        // DTO를 Redis에 보관 (TTL 10분)
        String redisKey = "TEMP_SOCIAL_SIGNUP:" + email;
        redisUtil.set(redisKey, tempSocialSignupRequest, 10);

        log.info("[소셜 공통 가입] DB 업데이트 대기 및 Redis 임시 저장 완료, email: {}", email);

        String tempToken = jwtUtil.createTempToken(email);
        return SocialSignupResponse.of(tempToken, email, request.nickname());
    }

    /** ================= 공통 메소드 ================= */

    // 소셜 독자
    private SignupResponse processSocialReaderSignup(ReaderSignupRequest request, String email, TempSocialSignupRequest tempRequest, String redisKey) {

        User user = User.socialUser(email, "SOCIAL_LOGIN_USER", UserRole.READER);

        user.updateForSocialSignup(
                tempRequest.nickname()
                , tempRequest.phoneNo()
                , tempRequest.birthday()
        );

        User savedUser = userRepository.save(user);

        SocialAuth socialAuth = SocialAuth.register(
                tempRequest.providerSns()
                , tempRequest.providerId()
                , savedUser.getId()
        );

        if(user.isAdult()) {
            user.verifyAdult(); // 19살 이상일 시 "인증 완료"
        }

        socialAuthRepository.save(socialAuth);

        ReaderProfile readerProfile = ReaderProfile.register(
                user.getId()
                , request.mainGenreToString()
                , request.readingGoal()
        );

        readerProfileRepository.save(readerProfile);
        adminCacheService.incrementNewUsersToday();

        return SignupResponse.of(user);
    }

    // 일반 독자
    private SignupResponse processNormalReaderSignup(ReaderSignupRequest request, String email, TempSignupRequest tempRequest, String redisKey) {

        // 중복 가입 체크
        if(userRepository.existsByEmail(email)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_ALREADY_COMPLETED_SIGNUP);
        }

        User user = User.register(
                tempRequest.email(),
                tempRequest.password(),
                tempRequest.nickname(),
                tempRequest.phoneNo(),
                tempRequest.birthday(),
                UserRole.READER
        );
        User savedUser = userRepository.save(user);

        ReaderProfile readerProfile = ReaderProfile.register(
                savedUser.getId()
                , request.mainGenreToString()
                , request.readingGoal()
        );

        if(user.isAdult()) {
            user.verifyAdult(); // 19살 이상일 시 "인증 완료"
        }

        readerProfileRepository.save(readerProfile);
        adminCacheService.incrementNewUsersToday();

        return SignupResponse.of(savedUser);
    }

    // 소셜 작가
    private SignupResponse processSocialAuthorSignup(AuthorRequest request, String email, TempSocialSignupRequest tempRequest, String redisKey) {

        if (userRepository.existsByEmail(email)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_ALREADY_COMPLETED_SIGNUP);
        }

        User user = User.socialUser(email, "SOCIAL_LOGIN_USER", UserRole.AUTHOR);

        user.updateForSocialSignup(
                tempRequest.nickname()
                , tempRequest.phoneNo()
                , tempRequest.birthday()
        );

        User savedUser = userRepository.save(user);

        SocialAuth socialAuth = SocialAuth.register(
                tempRequest.providerSns()
                , tempRequest.providerId()
                , savedUser.getId()
        );

        socialAuthRepository.save(socialAuth);

        AuthorProfile authorProfile = AuthorProfile.register(
                savedUser.getId()
                , request.bio()
                , request.careerLevel()
                , request.mainGenreToString()
                , request.instagramLinks()
                , request.xLinks()
                , request.blogLinks()
                , request.allowMenteeRequest()
        );

        if(savedUser.isAdult()) {
            savedUser.verifyAdult();
        }

        authorProfileRepository.save(authorProfile);
        adminCacheService.incrementNewUsersToday();

        return SignupResponse.of(savedUser);
    }

    // 일반 작가
    private SignupResponse processNormalAuthorSignup(AuthorRequest request, String email, TempSignupRequest tempRequest, String redisKey) {

        if(userRepository.existsByEmail(email)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_ALREADY_COMPLETED_SIGNUP);
        }

        User user = User.register(
                tempRequest.email()
                , tempRequest.password()
                , tempRequest.nickname()
                , tempRequest.phoneNo()
                , tempRequest.birthday()
                , UserRole.AUTHOR
        );

        User savedUser = userRepository.save(user);

        AuthorProfile authorProfile = AuthorProfile.register(
                savedUser.getId()
                , request.bio()
                , request.careerLevel()
                , request.mainGenreToString()
                , request.instagramLinks()
                , request.xLinks()
                , request.blogLinks()
                , request.allowMenteeRequest()
        );

        if(user.isAdult()) {
            user.verifyAdult(); // 19살 이상일 시 "인증 완료"
        }
        authorProfileRepository.save(authorProfile);
        adminCacheService.incrementNewUsersToday();

        return SignupResponse.of(savedUser);
    }

    /** ====== 관리자 회원 가입 ======
     이메일, 비밀번호, 핸드폰 인증만 진행
     ============================= */
    @Transactional
    public AdminSignupResponse adminSignup(AdminSignupRequest request, String email) {

        // 이메일 중복 확인
        checkEmail(request.email());

        // 회원가입 상태 조회와 삭제를 동시에 진행
        String smsToken = "SMS:TOKEN:" + request.tempToken();
        String verifiedPhone = (String) redisUtil.getAndDelete(smsToken);

        // 폰 인증 UUID 임시 토큰 검증, 토큰 안의 폰 번호와 입력한 폰 번호가 다르면 에러
        if (verifiedPhone == null || !verifiedPhone.equals(request.phoneNo())) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_PHONE_NOT_VERIFIED);
        }

        String encoderPassword = passwordEncoder.encode(request.password());

        User admin = User.registerAdmin(
                request.email(),
                encoderPassword,
                request.phoneNo(),
                UserRole.PENDING_ADMIN
        );
        userRepository.save(admin);

        log.info("관리자 가입 완료 - email: {}", email);

        return AdminSignupResponse.of(admin);
    }

    // SMS 공통 메소드
    private void consumePhoneVerification(String tempToken,String phoneNo) {
        String smsToken = "SMS:TOKEN:" + tempToken;
        String verifiedPhone = (String) redisUtil.getAndDelete(smsToken);
        if(verifiedPhone == null || !verifiedPhone.equals(phoneNo)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_PHONE_NOT_VERIFIED);
        }
    }
}
