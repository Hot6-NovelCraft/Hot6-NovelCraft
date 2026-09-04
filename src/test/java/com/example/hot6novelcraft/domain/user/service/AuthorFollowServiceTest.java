package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.user.dto.response.AuthorFollowResponse;
import com.example.hot6novelcraft.domain.user.entity.AuthorFollow;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorFollowRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class AuthorFollowServiceTest {

    @Mock
    AuthorFollowRepository authorFollowRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    AuthorFollowService authorFollowService;

    // 독자 Mock (userId = 1L)
    private UserDetailsImpl 독자() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getRole()).willReturn(UserRole.READER);

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(user);
        return userDetails;
    }

    // 작가 대상 Mock (userId 지정)
    private User 작가(Long userId) {
        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(user.getRole()).willReturn(UserRole.AUTHOR);
        return user;
    }

    // 독자 대상 Mock
    private User 독자타겟() {
        User user = mock(User.class);
        given(user.getRole()).willReturn(UserRole.READER);
        return user;
    }

    // ==================== 팔로우 ====================

    @Test
    void 팔로우_처음이면_생성_성공() {
        UserDetailsImpl userDetails = 독자();
        User targetAuthor = 작가(2L);

        given(userRepository.findById(2L)).willReturn(Optional.of(targetAuthor));
        given(authorFollowRepository.findByFollowerIdAndFollowingId(1L, 2L))
                .willReturn(Optional.empty());

        AuthorFollowResponse response = authorFollowService.toggleFollow(2L, userDetails);

        assertTrue(response.isFollowing());
    }

    @Test
    void 팔로우_이미있으면_취소_성공() {
        UserDetailsImpl userDetails = 독자();
        User targetAuthor = 작가(2L);
        AuthorFollow existing = mock(AuthorFollow.class);

        given(userRepository.findById(2L)).willReturn(Optional.of(targetAuthor));
        given(authorFollowRepository.findByFollowerIdAndFollowingId(1L, 2L))
                .willReturn(Optional.of(existing));

        AuthorFollowResponse response = authorFollowService.toggleFollow(2L, userDetails);

        assertFalse(response.isFollowing());
    }

    @Test
    void 팔로우_본인이면_실패() {
        UserDetailsImpl userDetails = 독자(); // userId = 1L

        assertThrows(ServiceErrorException.class,
                () -> authorFollowService.toggleFollow(1L, userDetails));
    }

    @Test
    void 팔로우_유저없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        given(userRepository.findById(2L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> authorFollowService.toggleFollow(2L, userDetails));
    }

    @Test
    void 팔로우_작가아니면_실패() {
        UserDetailsImpl userDetails = 독자();
        User targetReader = 독자타겟();

        given(userRepository.findById(2L)).willReturn(Optional.of(targetReader));

        assertThrows(ServiceErrorException.class,
                () -> authorFollowService.toggleFollow(2L, userDetails));
    }
}