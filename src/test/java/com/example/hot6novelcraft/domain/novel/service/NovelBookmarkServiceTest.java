package com.example.hot6novelcraft.domain.novel.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelBookmarkResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.NovelBookmark;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.repository.NovelBookmarkRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
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
class NovelBookmarkServiceTest {

    @Mock
    NovelBookmarkRepository novelBookmarkRepository;

    @Mock
    NovelRepository novelRepository;

    @InjectMocks
    NovelBookmarkService novelBookmarkService;

    // 독자 Mock (userId = 1L)
    private UserDetailsImpl 독자() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getRole()).willReturn(UserRole.READER);

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUser()).willReturn(user);
        return userDetails;
    }

    // 소설 Mock (authorId, status 지정)
    private Novel 소설(Long authorId, NovelStatus status) {
        Novel novel = mock(Novel.class);
        given(novel.getAuthorId()).willReturn(authorId);
        given(novel.getStatus()).willReturn(status);
        given(novel.isDeleted()).willReturn(false);
        return novel;
    }

    // ==================== 찜 ====================

    @Test
    void 찜_처음이면_생성_성공() {
        UserDetailsImpl userDetails = 독자();
        Novel novel = 소설(2L, NovelStatus.ONGOING); // 다른 작가 소설

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(novelBookmarkRepository.findByUserIdAndNovelId(1L, 1L))
                .willReturn(Optional.empty());

        NovelBookmarkResponse response = novelBookmarkService.toggleBookmark(1L, userDetails);

        assertTrue(response.isBookmarked());
    }

    @Test
    void 찜_이미있으면_취소_성공() {
        UserDetailsImpl userDetails = 독자();
        Novel novel = 소설(2L, NovelStatus.ONGOING);
        NovelBookmark existing = mock(NovelBookmark.class);

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));
        given(novelBookmarkRepository.findByUserIdAndNovelId(1L, 1L))
                .willReturn(Optional.of(existing));

        NovelBookmarkResponse response = novelBookmarkService.toggleBookmark(1L, userDetails);

        assertFalse(response.isBookmarked());
    }

    @Test
    void 찜_소설없으면_실패() {
        UserDetailsImpl userDetails = 독자();

        given(novelRepository.findById(1L)).willReturn(Optional.empty());

        assertThrows(ServiceErrorException.class,
                () -> novelBookmarkService.toggleBookmark(1L, userDetails));
    }

    @Test
    void 찜_삭제된소설이면_실패() {
        UserDetailsImpl userDetails = 독자();
        Novel novel = mock(Novel.class);
        given(novel.isDeleted()).willReturn(true);

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> novelBookmarkService.toggleBookmark(1L, userDetails));
    }

    @Test
    void 찜_본인소설이면_실패() {
        UserDetailsImpl userDetails = 독자(); // userId = 1L
        Novel novel = 소설(1L, NovelStatus.ONGOING); // authorId = 1L (본인)

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> novelBookmarkService.toggleBookmark(1L, userDetails));
    }

    @Test
    void 찜_PENDING상태면_실패() {
        UserDetailsImpl userDetails = 독자();
        Novel novel = 소설(2L, NovelStatus.PENDING); // 보류 상태

        given(novelRepository.findById(1L)).willReturn(Optional.of(novel));

        assertThrows(ServiceErrorException.class,
                () -> novelBookmarkService.toggleBookmark(1L, userDetails));
    }
}