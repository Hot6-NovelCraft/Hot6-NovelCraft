package com.example.hot6novelcraft.domain.novel.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelBookmarkResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.NovelBookmark;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.repository.NovelBookmarkRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NovelBookmarkService {

    private final NovelBookmarkRepository novelBookmarkRepository;
    private final NovelRepository novelRepository;

    // 소설 찜 (찜 / 취소)
    @Transactional
    public NovelBookmarkResponse toggleBookmark(Long novelId, UserDetailsImpl userDetails) {

        Long userId = userDetails.getUser().getId();

        // 소설 조회
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        // 삭제된 소설 체크
        if (novel.isDeleted()) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_ALREADY_DELETED);
        }

        // 본인 소설 찜 방지
        if (Objects.equals(novel.getAuthorId(), userId)) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_SELF_BOOKMARK_NOT_ALLOWED);
        }

        // 볼 수 있는 상태만 찜 가능 (PENDING 제외)
        if (novel.getStatus() == NovelStatus.PENDING) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_VIEWABLE);
        }

        // 찜 여부 확인
        Optional<NovelBookmark> existing = novelBookmarkRepository
                .findByUserIdAndNovelId(userId, novelId);

        // 이미 찜 -> 취소
        if (existing.isPresent()) {
            novelBookmarkRepository.delete(existing.get());
            novelRepository.decrementBookmarkCount(novelId);
            return NovelBookmarkResponse.of(false);
        }

        // 찜 생성
        try {
            NovelBookmark bookmark = NovelBookmark.builder()
                    .userId(userId)
                    .novelId(novelId)
                    .build();
            novelBookmarkRepository.save(bookmark);
            novelRepository.incrementBookmarkCount(novelId);
            return NovelBookmarkResponse.of(true);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 이미 찜 저장된 경우 -> 성공으로 처리
            return NovelBookmarkResponse.of(true);
        }
    }
}