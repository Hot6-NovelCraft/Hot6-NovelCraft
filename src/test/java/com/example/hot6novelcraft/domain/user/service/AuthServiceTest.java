package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.WithdrawalPendingException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.novel.entity.enums.MainGenre;
import com.example.hot6novelcraft.domain.user.dto.request.*;
import com.example.hot6novelcraft.domain.user.dto.response.*;
import com.example.hot6novelcraft.domain.user.entity.AuthorProfile;
import com.example.hot6novelcraft.domain.user.entity.ReaderProfile;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.entity.enums.ReadingGoal;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.ReaderProfileRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserCacheService userCacheService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private RedisUtil redisUtil;
    @Mock private AuthorProfileRepository authorProfileRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UserDetailsImpl userDetails;
    private final String TEST_EMAIL = "test@example.com";
    private final String TEST_PASSWORD = "password123!";

    @BeforeEach
    void setUp() {
        testUser = User.register(TEST_EMAIL, "encodedPw", "테스터", "01012345678", LocalDate.of(1995, 1, 1), UserRole.READER);
        ReflectionTestUtils.setField(testUser, "id", 1L);
        userDetails = new UserDetailsImpl(testUser, null);
    }

    @Nested
    @DisplayName("로그인 테스트")
    class LoginTest {
        @Test
        @DisplayName("[성공] 일반 로그인 성공 - 토큰 발급 확인")
        void login_success() {
            // given
            LoginUserRequest request = new LoginUserRequest(TEST_EMAIL, TEST_PASSWORD);
            Authentication auth = mock(Authentication.class);
            given(auth.getPrincipal()).willReturn(userDetails);
            given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).willReturn(auth);
            given(jwtUtil.createAccessToken(any())).willReturn("access-token");
            given(jwtUtil.createRefreshToken(anyString())).willReturn("refresh-token");

            // when
            LoginUserResponse response = authService.login(request);

            // then
            assertThat(response.accessToken()).isEqualTo("access-token");
            verify(userCacheService).saveRefreshToken(eq(TEST_EMAIL), anyString(), anyLong());
        }

        @Test
        @DisplayName("[실패] 탈퇴 유예 상태 유저 로그인 시도 -> WithdrawalPendingException 발생")
        void login_fail_deletedUser() {
            // given
            testUser.withdraw(); // isDeleted = true
            LoginUserRequest request = new LoginUserRequest(TEST_EMAIL, TEST_PASSWORD);
            Authentication auth = mock(Authentication.class);
            given(auth.getPrincipal()).willReturn(userDetails);
            given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).willReturn(auth);

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(WithdrawalPendingException.class);
        }
    }

    @Nested
    @DisplayName("내 정보 조회 테스트")
    class GetMyPageTest {
        @Test
        @DisplayName("[성공] 독자 유저 마이페이지 조회")
        void getMyPage_reader_success() {
            // given
            ReaderProfile profile = ReaderProfile.register(1L, "FANTASY", ReadingGoal.LIGHT);
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
            given(readerProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));

            // when
            MyPageResponse response = authService.getMyPage(userDetails);

            // then
            assertThat(response.nickname()).isEqualTo("테스터");
            assertThat(response.role()).isEqualTo(UserRole.READER);
        }
    }

    @Nested
    @DisplayName("회원 정보 수정 테스트")
    class UpdateUserInfoTest {
        @Test
        @DisplayName("[성공] 공통 정보 수정 (닉네임)")
        void updateUserInfo_success() {
            // given
            CommonUpdateRequest request = new CommonUpdateRequest("새닉네임", null);
            given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(testUser));
            given(userRepository.existsByNicknameAndIdNot("새닉네임", 1L)).willReturn(false);

            // when
            authService.updateUserInfo(request, userDetails);

            // then
            assertThat(testUser.getNickname()).isEqualTo("새닉네임");
        }

        @Test
        @DisplayName("[성공] 작가 프로필 수정")
        void authorUpdateProfile_success() {
            // given
            testUser.changeRole(UserRole.AUTHOR);
            AuthorRequest request = new AuthorRequest(List.of(MainGenre.FANTASY), "한줄소개", CareerLevel.ELEMENTARY, null, null, null, true);
            AuthorProfile profile = AuthorProfile.register(1L, "이전소개", CareerLevel.INTERMEDIATE, "FANTASY", null, null, null, true);

            given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(testUser));
            given(authorProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));

            // when
            authService.authorUpdateProfile(request, userDetails);

            // then
            assertThat(profile.getBio()).isEqualTo("한줄소개");
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 테스트")
    class PasswordUpdateTest {
        @Test
        @DisplayName("[성공] 비밀번호 변경 완료")
        void updatePassword_success() {
            // given
            given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("oldPw", "encodedPw")).willReturn(true);
            given(passwordEncoder.matches("newPw", "encodedPw")).willReturn(false);
            given(passwordEncoder.encode("newPw")).willReturn("newEncodedPw");

            // when
            authService.updatePassword("oldPw", "newPw", userDetails);

            // then
            assertThat(testUser.getPassword()).isEqualTo("newEncodedPw");
        }

        @Test
        @DisplayName("[실패] 소셜 로그인 유저는 비밀번호 변경 불가")
        void updatePassword_fail_socialUser() {
            // given
            ReflectionTestUtils.setField(testUser, "password", "SOCIAL_LOGIN");
            given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(testUser));

            // when & then
            assertThatThrownBy(() -> authService.updatePassword("any", "any", userDetails))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(UserExceptionEnum.ERR_SOCIAL_USER_CANNOT_CHANGE_PASSWORD.getMessage());
        }
    }

    @Nested
    @DisplayName("회원 탈퇴 및 복구 테스트")
    class WithdrawalAndRestoreTest {
        @Test
        @DisplayName("[성공] 회원 탈퇴(유예 상태로 변경)")
        void withdrawUser_success() {
            // given
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(testUser));

            // when
            authService.withdrawUser("token", TEST_EMAIL);

            // then
            assertThat(testUser.isDeleted()).isTrue();
            verify(userCacheService).deleteRefreshToken(TEST_EMAIL);
        }

        @Test
        @DisplayName("[성공] 계정 복구 (isDeleted=false로 변경)")
        void restoreUser_success() {
            // given
            testUser.withdraw();
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(testUser));

            // when
            authService.restoreUser(TEST_EMAIL);

            // then
            assertThat(testUser.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("[성공] 계정 즉시 파기(비식별화)")
        void abandonRecovery_success() {
            // given
            testUser.withdraw();
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(testUser));

            // when
            authService.abandonRecovery(TEST_EMAIL);

            // then
            assertThat(testUser.getEmail()).contains("deleted_");
            assertThat(testUser.getNickname()).contains("알수없음_");
        }
    }

    @Test
    @DisplayName("[성공] 로그아웃 - 리프레시 토큰 삭제 및 블랙리스트 등록")
    void logout_success() {
        // given
        String accessToken = "Bearer valid-token";
        given(jwtUtil.substringToken(accessToken)).willReturn("valid-token");
        given(jwtUtil.getExpiration("valid-token")).willReturn(1000L);

        // when
        authService.logout(accessToken, TEST_EMAIL);

        // then
        verify(userCacheService).deleteRefreshToken(TEST_EMAIL);
        verify(redisUtil).setBlackList("valid-token", "Logout", Duration.ofMillis(1000L));
    }
}