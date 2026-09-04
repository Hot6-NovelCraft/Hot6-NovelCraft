package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.domain.user.dto.response.AuthorFollowResponse;
import com.example.hot6novelcraft.domain.user.entity.AuthorFollow;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorFollowRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthorFollowService {

    private final AuthorFollowRepository authorFollowRepository;
    private final UserRepository userRepository;

    // 작가 팔로우 토글 (팔로우 / 취소)
    @Transactional
    public AuthorFollowResponse toggleFollow(Long authorId, UserDetailsImpl userDetails) {

        Long followerId = userDetails.getUser().getId();

        // 본인 팔로우 방지
        if (Objects.equals(followerId, authorId)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_SELF_FOLLOW_NOT_ALLOWED);
        }

        // 팔로우 대상이 작가인지 확인
        User targetUser = userRepository.findById(authorId)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        // 작가 아닌사람한테 팔로우 못함!!!!! 검증
        if (targetUser.getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_NOT_AUTHOR);
        }

        // 팔로우 여부 확인
        Optional<AuthorFollow> existing = authorFollowRepository
                .findByFollowerIdAndFollowingId(followerId, authorId);

        // 이미 팔로우 -> 취소
        if (existing.isPresent()) {
            authorFollowRepository.delete(existing.get());
            return AuthorFollowResponse.of(false);
        }

        // 팔로우 생성
        try {
            AuthorFollow follow = AuthorFollow.builder()
                    .followerId(followerId)
                    .followingId(authorId)
                    .build();
            authorFollowRepository.save(follow);
            return AuthorFollowResponse.of(true);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 이미 팔로우 저장된 경우 -> 성공으로 처리
            return AuthorFollowResponse.of(true);
        }
    }
}