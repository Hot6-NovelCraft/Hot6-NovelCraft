package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.admin.service.AdminCacheService;
import com.example.hot6novelcraft.domain.novel.entity.enums.MainGenre;
import com.example.hot6novelcraft.domain.user.dto.request.*;
import com.example.hot6novelcraft.domain.user.dto.response.AdminSignupResponse;
import com.example.hot6novelcraft.domain.user.dto.response.SocialSignupResponse;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.entity.enums.ProviderSns;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.ReaderProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.SocialAuthRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignupService 단위 테스트")
class SignupServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthorProfileRepository authorProfileRepository;

    @Mock
    private ReaderProfileRepository readerProfileRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SocialAuthRepository socialAuthRepository;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private AdminCacheService adminCacheService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private SignupService signupService;

    // ==================== 공통 픽스처 ====================
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PHONE = "01012345678";
    private static final String TEST_NICKNAME = "테스터";
    private static final String TEST_PASSWORD = "password123!";
    private static final String REDIS_VERIFIED_KEY = "SMS:VERIFIED:" + TEST_PHONE;
    private static final String FAKE_TEMP_TOKEN = "Bearer eyJhbGci.test.token";

    private User createUser(boolean isDeleted) {
        User user = User.register(
                TEST_EMAIL, "encodedPassword", TEST_NICKNAME, TEST_PHONE,
                LocalDate.of(1995, 1, 1), UserRole.READER
        );
        if (isDeleted) {
            user.withdraw(); // 엔티티에 구현한 탈퇴 로직 호출 (isDeleted=true 세팅)
        }
        return user;
    }

    private User createAutheorUser() {
        return User.register(
                TEST_EMAIL,
                "encodedPassword",
                TEST_NICKNAME,
                TEST_PHONE,
                LocalDate.of(1995, 1, 1),
                UserRole.READER
        );
    }

    // ==================== Redis SMS 인증 공통 세팅 ====================
    private void mockSmsVerified(String phoneNo) {
        lenient().when(redisUtil.getAndDelete(anyString())).thenReturn(phoneNo);
        lenient().when(redisUtil.get(anyString())).thenReturn(phoneNo);
    }

    private void mockSmsNotVerified(String phoneNo) {
        lenient().when(redisUtil.getAndDelete(anyString())).thenReturn(null);
        lenient().when(redisUtil.get(anyString())).thenReturn(null);
    }

    // ====================================================================
    // 공통 회원가입 테스트
    // ====================================================================
    @Nested
    @DisplayName("공통 회원가입 (commonSignup)")
    class CommonSignupTest {

        private CommonSignupRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new CommonSignupRequest(
                    TEST_EMAIL
                    , TEST_PASSWORD
                    , TEST_NICKNAME
                    , LocalDate.of(1995, 1, 1)
                    , TEST_PHONE
                    , "test-token"
            );
        }

        @Test
        @DisplayName("[성공] SMS 인증 완료 + 중복 없음 → 임시 토큰 반환")
        void commonSignup_success() {

            // given
            mockSmsVerified(TEST_PHONE);

            // exists 대신 findByEmail/Nickname이 Optional.empty()를 반환하도록 세팅
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());
            given(userRepository.findByNickname(TEST_NICKNAME)).willReturn(Optional.empty());
            given(passwordEncoder.encode(TEST_PASSWORD)).willReturn("encodedPassword");
            given(jwtUtil.createTempToken(TEST_EMAIL)).willReturn(FAKE_TEMP_TOKEN);

            // when
            String result = signupService.commonSignup(validRequest);

            // then
            assertThat(result).isEqualTo(FAKE_TEMP_TOKEN);
            verify(redisUtil).getAndDelete(anyString());
            verify(redisUtil).set(
                    eq("TEMP_SIGNUP:" + TEST_EMAIL),
                    any(TempSignupRequest.class),
                    anyLong()
            );
        }

        @Test
        @DisplayName("[실패] SMS 인증 미완료 → ERR_PHONE_NOT_VERIFIED 예외")
        void commonSignup_fail_phoneNotVerified() {

            // given
            mockSmsNotVerified(TEST_PHONE);

            // when & then
            assertThatThrownBy(() -> signupService.commonSignup(validRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_PHONE_NOT_VERIFIED.getMessage());
        }

        @Test
        @DisplayName("[실패] 이메일 중복 (활성 유저) → ERR_EMAIL_ALREADY_EXISTS")
        void commonSignup_fail_emailDuplicated() {

            // given
            mockSmsVerified(TEST_PHONE);
            // 활성 상태의 유저 존재 가정
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(createUser(false)));

            // when & then
            assertThatThrownBy(() -> signupService.commonSignup(validRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_EMAIL_ALREADY_EXISTS.getMessage());
        }

        @Test
        @DisplayName("[실패] 닉네임 중복 (활성 유저) → ERR_NICKNAME_ALREADY_EXISTS")
        void commonSignup_fail_nicknameDuplicated() {
            mockSmsVerified(TEST_PHONE);

            // given
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());
            given(userRepository.findByNickname(TEST_NICKNAME)).willReturn(Optional.of(createUser(false)));

            // when & then
            assertThatThrownBy(() -> signupService.commonSignup(validRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_NICKNAME_ALREADY_EXISTS.getMessage());
            verify(userRepository, never()).save(any());
        }

        // ==========================================
        // 탈퇴 유예 기간 유저 케이스 (이메일)
        // ==========================================
        @Test
        @DisplayName("[실패] 이메일 중복 (30일 이내 탈퇴 유예 유저) → ERR_USER_WITHDRAWAL_PENDING_CONFLICT")
        void commonSignup_fail_emailWithdrawalPending() {

            mockSmsVerified(TEST_PHONE);
            // 탈퇴 상태의 유저 존재 가정
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(createUser(true)));

            assertThatThrownBy(() -> signupService.commonSignup(validRequest))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_CONFLICT.getMessage());
        }
    }

    // ====================================================================
    // 작가 추가 가입 테스트 (2단계)
    // ====================================================================
    @Nested
    @DisplayName("작가 추가 가입 (authorSignup)")
    class AuthorSignupTest {

        private AuthorRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new AuthorRequest(List.of(MainGenre.FANTASY), "소개", CareerLevel.INTERMEDIATE, null, null, null, true);
        }

        @Test
        @DisplayName("[실패] Redis 바구니가 만료되었거나 없을 때 → ERR_INVALID_TOKEN 예외")
        void authorSignup_fail_noBasket() {
            // given
            given(redisUtil.getAndDelete(contains("TEMP_SIGNUP:"))).willReturn(null);
            given(redisUtil.getAndDelete(contains("TEMP_SOCIAL_SIGNUP:"))).willReturn(null);

            // when & then
            assertThatThrownBy(() -> signupService.authorSignup(validRequest, TEST_EMAIL))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_INVALID_TOKEN.getMessage());
        }

        @Test
        @DisplayName("[실패] 이미 가입 완료된 AUTHOR 유저 → ERR_ALREADY_COMPLETED_SIGNUP 예외")
        void authorSignup_fail_alreadyCompleted() {
            // given
            User authorUser = User.register(TEST_EMAIL, "pw", TEST_NICKNAME, TEST_PHONE, LocalDate.now(), UserRole.AUTHOR);
            // 바구니도 없는 상태 가정
            TempSignupRequest tempDto = new TempSignupRequest(TEST_EMAIL, "pw", TEST_NICKNAME, TEST_PHONE, LocalDate.now());
            given(redisUtil.getAndDelete(contains("TEMP_SOCIAL_SIGNUP"))).willReturn(null);
            given(redisUtil.getAndDelete(contains("TEMP_SIGNUP"))).willReturn(tempDto);
            given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> signupService.authorSignup(validRequest, TEST_EMAIL))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_ALREADY_COMPLETED_SIGNUP.getMessage());
        }
    }

    // ====================================================================
    // 소셜 공통 가입 테스트
    // ====================================================================
    @Nested
    @DisplayName("소셜 공통 가입 (socialCommonSignup)")
    class SocialCommonSignupTest {

        private SocialSignupRequest validRequest;
        private static final String PROVIDER_ID = "google-sub-12345";

        @BeforeEach
        void setUp() {
            validRequest = new SocialSignupRequest(
                    TEST_NICKNAME
                    , LocalDate.of(1995,1,1)
                    , TEST_PHONE
                    , "test-social-token"
            );
        }

        @Test
        @DisplayName("[성공] 소셜 추가 정보 입력 → Redis 임시 저장 및 tempToken 반환")
        void socialCommonSignup_success() {
            // given
            String phoneToken = "test-social-token";
            String testPhone = "01012345678";
            String testEmail = "test@example.com";

            // 파라미터 순서 주의: nickname, birthDay, phoneNo, tempToken
            SocialSignupRequest request = new SocialSignupRequest(
                    "테스터", LocalDate.of(1995, 1, 1), testPhone, phoneToken
            );

            given(redisUtil.getAndDelete("SMS:TOKEN:" + phoneToken)).willReturn(testPhone);
            given(userRepository.findByNickname(request.nickname())).willReturn(Optional.empty());
            // 신규 가입이므로 이메일로 조회했을 때 아무도 없어야 함
            given(userRepository.findByEmail(testEmail)).willReturn(Optional.empty());

            // [수정 4] Null 에러 방지를 위한 JwtUtil Mocking 추가!!!
            given(jwtUtil.createTempToken(testEmail)).willReturn("mock-temp-token");

            // when
            SocialSignupResponse response = signupService.socialCommonSignup(request, testEmail, "google-sub-123", ProviderSns.GOOGLE);

            // then
            assertThat(response).isNotNull();
            assertThat(response.tempToken()).isEqualTo("mock-temp-token"); // jwtUtil이 반환한 토큰과 일치하는지 확인

            // Redis에 10분 TTL로 잘 세팅되었는지 확인
            verify(redisUtil).set(eq("TEMP_SOCIAL_SIGNUP:" + testEmail), any(TempSocialSignupRequest.class), eq(10L));
        }

        @Test
        @DisplayName("[실패] SMS 미인증 상태로 소셜 가입 시도 → ERR_PHONE_NOT_VERIFIED 예외")
        void socialCommonSignup_fail_phoneNotVerified() {

            // given
            mockSmsNotVerified(TEST_PHONE);

            // when
            assertThatThrownBy(() -> signupService.socialCommonSignup(
                    validRequest, TEST_EMAIL, PROVIDER_ID, ProviderSns.GOOGLE
            ))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_PHONE_NOT_VERIFIED.getMessage());

            // then
            verify(userRepository, never()).findByEmail(any());
            verify(socialAuthRepository, never()).save(any());
        }

        @Test
        @DisplayName("[실패] 탈퇴 유예 기간(30일 이내) 이메일로 소셜 가입 시도 → ERR_USER_WITHDRAWAL_PENDING_CONFLICT")
        void socialCommonSignup_fail_withdrawalPending() {
            // given
            mockSmsVerified(TEST_PHONE);
            given(userRepository.findByNickname(TEST_NICKNAME)).willReturn(Optional.empty()); // 닉네임은 사용 가능

            // DB에 존재하는 유저가 탈퇴 상태(isDeleted=true)인 경우를 가정
            User withdrawnUser = createUser(true);
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(withdrawnUser));

            // when & then
            assertThatThrownBy(() -> signupService.socialCommonSignup(
                    validRequest, TEST_EMAIL, PROVIDER_ID, ProviderSns.GOOGLE
            ))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_USER_WITHDRAWAL_PENDING_CONFLICT.getMessage());
        }

        @Test
        @DisplayName("[실패] 닉네임 중복 → ERR_NICKNAME_ALREADY_EXISTS 예외")
        void socialCommonSignup_fail_nicknameExists() {
            // given
            String phoneToken = "test-social-token";
            String testPhone = "01012345678";
            String testEmail = "test@example.com";

            // [수정 1] 파라미터 순서 정확히 맞춤: nickname, birthDay, phoneNo, tempToken
            SocialSignupRequest request = new SocialSignupRequest(
                    "중복닉네임", LocalDate.of(1995, 1, 1), testPhone, phoneToken
            );

            // [수정 2] 휴대폰 인증 무사 통과
            given(redisUtil.getAndDelete("SMS:TOKEN:" + phoneToken)).willReturn(testPhone);

            // [수정 3] 닉네임 중복 검사 로직 맞춤 (findByNickname 반환 설정)
            User mockDuplicateUser = User.register("other@test.com", "pw", "중복닉네임", "01099998888", LocalDate.now(), UserRole.READER);
            given(userRepository.findByNickname(request.nickname())).willReturn(Optional.of(mockDuplicateUser));

            // when & then
            // Service 시그니처에 맞게 providerId, providerSns 추가
            assertThatThrownBy(() -> signupService.socialCommonSignup(request, testEmail, "google-sub-123", ProviderSns.GOOGLE))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessageContaining(UserExceptionEnum.ERR_NICKNAME_ALREADY_EXISTS.getMessage());
        }
    }
    // ====================================================================
    // 관리자 회원가입
    // ====================================================================

    @Nested
    @DisplayName("관리자 회원가입 (adminSignup)")
    class AdminSignupTest {

        @Test
        @DisplayName("[성공] 이메일, 핸드폰 인증 후 관리자 회원가입 완료")
        void adminSignup_success() {
            // given
            String tempToken = "admin-temp-token";
            AdminSignupRequest request = new AdminSignupRequest(
                    TEST_EMAIL, TEST_PASSWORD, TEST_PHONE, tempToken
            );

            // 이메일 중복 없음
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());
            // SMS 토큰 검증 통과
            given(redisUtil.getAndDelete("SMS:TOKEN:" + tempToken)).willReturn(TEST_PHONE);
            // 비밀번호 인코딩
            given(passwordEncoder.encode(TEST_PASSWORD)).willReturn("encodedAdminPw");

            // when
            AdminSignupResponse response = signupService.adminSignup(request, TEST_EMAIL);

            // then
            verify(userRepository).save(any(User.class)); // User 저장 로직 호출 확인
            assertThat(response.email()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("[실패] 폰 번호 인증 불일치 시 ERR_PHONE_NOT_VERIFIED 예외")
        void adminSignup_fail_phoneNotVerified() {
            // given
            String tempToken = "admin-temp-token";
            AdminSignupRequest request = new AdminSignupRequest(
                    TEST_EMAIL, TEST_PASSWORD, TEST_PHONE, tempToken
            );

            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());
            // Redis에서 가져온 폰 번호가 입력한 폰 번호와 다름
            given(redisUtil.getAndDelete("SMS:TOKEN:" + tempToken)).willReturn("01099998888");

            // when & then
            assertThatThrownBy(() -> signupService.adminSignup(request, TEST_EMAIL))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_PHONE_NOT_VERIFIED.getMessage());
        }
    }
}