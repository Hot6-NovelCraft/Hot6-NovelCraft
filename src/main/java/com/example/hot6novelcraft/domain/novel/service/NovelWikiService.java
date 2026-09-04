package com.example.hot6novelcraft.domain.novel.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.NovelWikiExceptionEnum;
import com.example.hot6novelcraft.domain.novel.dto.request.NovelWikiCreateRequest;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelWikiCreateResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelWikiDeleteResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelWikiResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.NovelWiki;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.novel.repository.NovelWikiRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NovelWikiService {

    private static final String WIKI_CACHE_KEY = "wiki::";
    private static final Duration WIKI_CACHE_TTL = Duration.ofHours(1); // 1시간

    private final NovelWikiRepository novelWikiRepository;
    private final NovelRepository novelRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // 설정집 저장
    @Transactional
    public NovelWikiCreateResponse createWiki(Long novelId, NovelWikiCreateRequest request,
                                              UserDetailsImpl userDetails) {
        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 소설 조회 (본인 소설 및 삭제여부)
        findNovelById(novelId, userDetails.getUser().getId());

        // 설정집 생성
        NovelWiki wiki = NovelWiki.createWiki(
                novelId,
                request.category(),
                request.title(),
                request.content()
        );

        NovelWiki savedWiki = novelWikiRepository.save(wiki);

        // 캐시 무효화
        evictWikiCacheSafely(novelId);

        return NovelWikiCreateResponse.from(savedWiki.getId());
    }

    // 설정집 삭제 (하드딜리트)
    @Transactional
    public NovelWikiDeleteResponse deleteWiki(Long novelId, Long wikiId, UserDetailsImpl userDetails) {

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 소설 조회 (본인 소설 및 삭제여부)
        findNovelById(novelId, userDetails.getUser().getId());

        // 설정집 조회
        NovelWiki wiki = novelWikiRepository.findById(wikiId)
                .orElseThrow(() -> new ServiceErrorException(NovelWikiExceptionEnum.WIKI_NOT_FOUND));

        // 해당 소설의 설정집인지 확인
        if (!Objects.equals(wiki.getNovelId(), novelId)) {
            throw new ServiceErrorException(NovelWikiExceptionEnum.WIKI_NOT_FOUND);
        }

        // 설정집 삭제 (하드딜리트)
        novelWikiRepository.delete(wiki);

        // 캐시 무효화
        evictWikiCacheSafely(novelId);

        return NovelWikiDeleteResponse.from(wikiId);
    }

    // 설정집 조회 (Redis 캐싱)
    @Transactional(readOnly = true)
    public List<NovelWikiResponse> getWikiList(Long novelId, UserDetailsImpl userDetails) {

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 소설 조회 (본인 소설 및 삭제여부)
        findNovelById(novelId, userDetails.getUser().getId());

        // Redis 캐시 확인
        String cacheKey = WIKI_CACHE_KEY + novelId;

        Object cached = null;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (RuntimeException e) {
            log.warn("Wiki cache read failed. key={}", cacheKey, e);
        }

        if (cached != null) {
            log.debug("===== [캐시 HIT] key={} =====", cacheKey);
            return (List<NovelWikiResponse>) cached;
        }

        log.debug("===== [캐시 MISS] key={} DB 조회 =====", cacheKey);

// DB 조회 (QueryDSL)
        List<NovelWiki> wikiList = novelWikiRepository.findAllByNovelId(novelId);

// Response 변환
        List<NovelWikiResponse> response = wikiList.stream()
                .map(NovelWikiResponse::from)
                .collect(Collectors.toList());

// Redis 캐싱
        try {
            redisTemplate.opsForValue().set(cacheKey, response, WIKI_CACHE_TTL);
            log.debug("===== [캐시 저장] key={} TTL=1시간 =====", cacheKey);
        } catch (RuntimeException e) {
            log.warn("Wiki cache write failed. key={}", cacheKey, e);
        }

        return response;
    }

    // 작가 권한 확인 공통 메서드
    private void validateAuthorRole(UserDetailsImpl userDetails) {
        if (userDetails.getUser().getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_AUTHOR_FORBIDDEN);
        }
    }

    // 소설 조회 공통 메서드 (본인 소설 및 삭제여부)
    private Novel findNovelById(Long novelId, Long userId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        // 본인 소설 검증
        if (!Objects.equals(novel.getAuthorId(), userId)) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_FORBIDDEN);
        }
        // 삭제 여부 검증
        if (novel.isDeleted()) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_ALREADY_DELETED);
        }
        return novel;
    }

    // 캐시 무효화 관련 공통 메서드
    private void evictWikiCacheSafely(Long novelId) {
        try {
            redisTemplate.delete(WIKI_CACHE_KEY + novelId);
        } catch (RuntimeException e) {
            log.warn("Wiki cache eviction failed. novelId={}", novelId, e);
        }
    }
}