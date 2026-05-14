package com.example.hot6novelcraft.domain.episode.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EpisodeExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.domain.episode.dto.cache.EpisodeContentCache;
import com.example.hot6novelcraft.domain.episode.dto.request.EpisodeCreateRequest;
import com.example.hot6novelcraft.domain.episode.dto.request.EpisodeUpdateRequest;
import com.example.hot6novelcraft.domain.episode.dto.response.*;
import com.example.hot6novelcraft.domain.episode.entity.Episode;
import com.example.hot6novelcraft.domain.episode.entity.enums.EpisodeStatus;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.enums.MainTag;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.point.entity.enums.PointHistoryType;
import com.example.hot6novelcraft.domain.point.repository.PointHistoryRepository;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.AuthorFollowRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodeService {

    private static final int EPISODE_PRICE = 200;
    private static final int FREE_EPISODE_LIMIT = 2;

    private final EpisodeRepository episodeRepository;
    private final NovelRepository novelRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final EpisodeCacheService episodeCacheService;
    private final UserRepository userRepository;
    private final AuthorFollowRepository authorFollowRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 회차 생성
    @Transactional
    public EpisodeCreateResponse createEpisode(Long novelId, EpisodeCreateRequest request, UserDetailsImpl userDetails) {

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 소설 조회(본인 소설 및 삭제여부)
        findNovelById(novelId, userDetails.getUser().getId());

        // 회차 번호 중복 확인(삭제된 회차 제외)
        if (episodeRepository.existsByNovelIdAndEpisodeNumberAndIsDeletedFalse(novelId, request.episodeNumber())) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NUMBER_DUPLICATE);
        }

        // 회차 번호 순서 검증 (ex : 1,2,3 ...10)
        int lastEpisodeNumber = episodeRepository.countByNovelIdAndIsDeletedFalse(novelId);
        if (request.episodeNumber() != lastEpisodeNumber + 1) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NUMBER_NOT_SEQUENTIAL);
        }

        // 무료/유료 자동 (1,2화 무료 / 3화부터 200포인트)
        boolean isFree = request.episodeNumber() <= FREE_EPISODE_LIMIT;
        int pointPrice = isFree ? 0 : EPISODE_PRICE;

        // 회차 생성 (초안 DRAFT)
        Episode episode = Episode.createEpisode(
                novelId,
                request.episodeNumber(),
                request.title(),
                request.content(),
                isFree,
                pointPrice
        );

        Episode savedEpisode = episodeRepository.save(episode);

        return EpisodeCreateResponse.from(savedEpisode.getId());
    }

    // 회차 수정
    @Transactional
    public EpisodeUpdateResponse updateEpisode(Long episodeId, EpisodeUpdateRequest request, UserDetailsImpl userDetails) {

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 회차 조회
        Episode episode = findEpisodeById(episodeId);

        // 본인 소설 회차인지 확인
        findNovelById(episode.getNovelId(), userDetails.getUser().getId());

        // 회차 수정
        episode.update(request.title(), request.content());

        // 캐시 무효화
        episodeCacheService.evictContentCache(episode.getId());

        return EpisodeUpdateResponse.from(episode.getId());
    }

    // 회차 삭제
    @Transactional
    public EpisodeDeleteResponse deleteEpisode(Long episodeId, UserDetailsImpl userDetails) {

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 회차 조회
        Episode episode = findEpisodeById(episodeId);

        // 본인 소설 회차인지 확인
        findNovelById(episode.getNovelId(), userDetails.getUser().getId());

        // 마지막 회차만 삭제 가능
        if (episodeRepository.existsByNovelIdAndEpisodeNumberGreaterThanAndIsDeletedFalse(
                episode.getNovelId(), episode.getEpisodeNumber())) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_DELETE_NOT_LAST);
        }

        // 회차 삭제 (소프트 딜리트)
        episode.delete();

        // 캐시 무효화
        episodeCacheService.evictContentCache(episode.getId());

        return EpisodeDeleteResponse.from(episode.getId());
    }

    // 회차 발행
    @Transactional
    public EpisodePublishResponse publishEpisode(Long episodeId, UserDetailsImpl userDetails) {

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 회차 조회
        Episode episode = findEpisodeById(episodeId);

        // 본인 소설 회차인지 확인
        Novel novel = findNovelById(episode.getNovelId(), userDetails.getUser().getId());

        // 이미 발행된 회차 확인
        if (episode.getStatus() == EpisodeStatus.PUBLISHED) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_PUBLISHED);
        }

        // 본문 내용 없으면 발행 불가
        if (episode.getContent() == null || episode.getContent().isBlank()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_CONTENT_EMPTY);
        }

        // 이전 회차 순서 검증 (1화부터 순서대로 발행)
        if (episodeRepository.existsByNovelIdAndEpisodeNumberLessThanAndStatusNotAndIsDeletedFalse(
                episode.getNovelId(), episode.getEpisodeNumber(), EpisodeStatus.PUBLISHED)) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_PREVIOUS_NOT_PUBLISHED);
        }

        // 회차 발행
        episode.publish();

        // 1화 발행 시 소설 연재중으로 변경
        if (episode.getEpisodeNumber() == 1) {
            novel.changeStatus(NovelStatus.ONGOING);
        }

        // 캐시 무효화
        episodeCacheService.evictContentCache(episode.getId());

        String authorNickname = userRepository.findById(novel.getAuthorId()).map(u -> u.getNickname()).orElse("작가");
        authorFollowRepository.findFollowerIdsByFollowingId(novel.getAuthorId())
                .forEach(followerId -> eventPublisher.publishEvent(
                        NotificationEvent.episodePublished(followerId, authorNickname, novel.getTitle(), episode.getId())));

        return EpisodePublishResponse.from(episode.getId());
    }

    // 회차 목록 조회 (QueryDSL + 인덱싱)
    @Transactional(readOnly = true)
    public PageResponse<EpisodeListResponse> getEpisodeList(Long novelId, Pageable pageable) {

        // 소설 존재 여부 확인
        novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        // 회차 목록 조회 (PUBLISHED만)
        Page<EpisodeListResponse> episodes = episodeRepository.findEpisodeListByNovelId(novelId, pageable);

        return PageResponse.register(episodes);
    }

    // 회차 본문 조회 V1 (JPA 단건 조회)
    @Transactional
    public EpisodeDetailResponse getEpisodeContentV1(Long episodeId, UserDetailsImpl userDetails) {

        Long userId = userDetails.getUser().getId();

        // 회차 조회
        Episode episode = findEpisodeById(episodeId);

        // 발행된 회차인지 확인
        if (episode.getStatus() != EpisodeStatus.PUBLISHED) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_PUBLISHED);
        }

        // 유료 회차 접근 제어 (PointHistory 이력 체크) - K6테스트할때만 주석처리
        validateEpisodeAccess(episode.getId(), episode.isFree(), userId);

        // 성인 컨텐츠 권한 확인 -서하나
        validateReaderAdultAccess(episode.getNovelId(), userDetails);


        // 소설 조회수 +1 (어뷰징 방지)
        increaseNovelViewCount(episode.getNovelId(), episode.getId(), userId);

        return EpisodeDetailResponse.from(episode);
    }

    // 회차 본문 조회 V2 (Hot Key + 캐싱)
    @Transactional
    public EpisodeDetailResponse getEpisodeContentV2(Long episodeId, UserDetailsImpl userDetails) {

        Long userId = userDetails.getUser().getId();

        // 단건 캐시 확인 (인기작은 이미 캐싱돼 있음)
        EpisodeContentCache cached = episodeCacheService.getContentCache(episodeId);

        if (cached != null) {
            validateEpisodeAccess(cached.episodeId(), cached.isFree(), userId);
            validateReaderAdultAccess(cached.novelId(), userDetails);

            increaseNovelViewCount(cached.novelId(), cached.episodeId(), userId);
            episodeCacheService.increaseHotKeyCount(cached.novelId());
            return toDetailResponse(cached);
        }

        // 캐시 미스 -> DB 조회
        EpisodeContentCache content = episodeRepository.findContentCacheById(episodeId);
        if (content == null) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND);
        }

        // 유료 회차 접근 제어 - K6테스트할때만 주석처리
        validateEpisodeAccess(content.episodeId(), content.isFree(), userId);

        // 핫키 카운터 증가
        long recentViews = episodeCacheService.increaseHotKeyCount(content.novelId());

        // 성인 컨텐츠 열람 권한 확인 - 서하나
        validateReaderAdultAccess(content.novelId(), userDetails);

        // 인기작이면 캐싱 (비인기작은 메모리 절약)
        if (episodeCacheService.isHotNovel(recentViews)) {
            episodeCacheService.saveContentCache(episodeId, content);
        }

        // 조회수 처리
        increaseNovelViewCount(content.novelId(), episodeId, userId);

        return toDetailResponse(content);
    }

    // EpisodeContentCache -> EpisodeDetailResponse 변환
    private EpisodeDetailResponse toDetailResponse(EpisodeContentCache cache) {
        return new EpisodeDetailResponse(
                cache.episodeId(),
                cache.episodeNumber(),
                cache.title(),
                cache.content(),
                cache.likeCount(),
                cache.isFree(),
                cache.pointPrice()
        );
    }

    // 작가용 회차 단건 조회 (에디터 본문 로드 - 포인트 차감 없음)
    @Transactional(readOnly = true)
    public AuthorEpisodeDetailResponse getAuthorEpisodeDetail(Long novelId, Long episodeId, UserDetailsImpl userDetails) {
        validateAuthorRole(userDetails);
        findNovelById(novelId, userDetails.getUser().getId());
        Episode episode = findEpisodeById(episodeId);
        if (!episode.getNovelId().equals(novelId)) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND);
        }
        return AuthorEpisodeDetailResponse.from(episode);
    }

    // 작가용 회차 목록 조회 (에디터용)
    @Transactional(readOnly = true)
    public PageResponse<AuthorEpisodeListResponse> getAuthorEpisodeList(Long novelId, UserDetailsImpl userDetails, Pageable pageable) {

        // 작가 권한 확인
        validateAuthorRole(userDetails);

        // 본인 소설 확인 (다른 작가 소설 회차 조회 방지)
        findNovelById(novelId, userDetails.getUser().getId());

        // 회차 목록 조회 (DRAFT 포함)
        Page<AuthorEpisodeListResponse> episodes =
                episodeRepository.findAuthorEpisodeList(novelId, pageable);

        return PageResponse.register(episodes);
    }


    // -----------------------------------------공통 매서드---------------------------------------------------------------

    // 작가 권한 확인 공통 메서드
    private void validateAuthorRole(UserDetailsImpl userDetails) {
        if (userDetails.getUser().getRole() != UserRole.AUTHOR) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_FORBIDDEN);
        }
    }

    // 소설 조회 공통 메서드(본인 소설 및 삭제여부)
    private Novel findNovelById(Long novelId, Long userId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        // 본인 소설 확인 먼저
        if (!Objects.equals(novel.getAuthorId(), userId)) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_FORBIDDEN);
        }

        // 삭제 여부
        if (novel.isDeleted()) {
            throw new ServiceErrorException(NovelExceptionEnum.NOVEL_ALREADY_DELETED);
        }

        return novel;
    }

    // 회차 조회 공통 메서드
    private Episode findEpisodeById(Long episodeId) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ServiceErrorException(EpisodeExceptionEnum.EPISODE_NOT_FOUND));

        if (episode.isDeleted()) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_ALREADY_DELETED);
        }
        return episode;
    }

    // 소설 조회수 +1 (어뷰징 방지)
    private void increaseNovelViewCount(Long novelId, Long episodeId, Long userId) {
        // 상태 값 검증 - 삭제, 보류, 휴재 상태 포함된 경우 종료 - 서하나
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        if (novel.isDeleted() ||
                novel.getStatus() == NovelStatus.PENDING ||
                novel.getStatus() == NovelStatus.HIATUS
        ) {
            log.debug("[조회수/랭킹 반영 제외] novelId: {} 은(는) 랭킹 및 조회수 집계 대상이 아닙니다.", novelId);
            return;
        }

        // 수정
        if (episodeCacheService.isFirstView(userId, novelId)) {
            // 소설 조회수 증가
            episodeCacheService.increaseViewCount(novelId);
        }

        // 회차 조회수 증가 (회차 단위 어뷰징 체크)
        if (episodeCacheService.isFirstEpisodeView(userId, episodeId)) {
            episodeCacheService.increaseEpisodeDailyViewCount(episodeId);
        }

        episodeCacheService.increaseRankingScore(novelId);
        // 성인물 체크 로직 - 서하나
        boolean isAdult = novel.getTags() != null &&
                (novel.getTags().contains(MainTag.ADULT.name()));

        // 퍼블릭 메인 랭킹 반영 여부 결정
        if (!isAdult) {
            // 일반 작품만 전체 랭킹 점수 증가
            episodeCacheService.increaseRankingScore(novelId);
        } else {
            // 성인 작품은 메인 랭킹 적재 차단 (추후 성인물 전용 랭킹이 생긴다면 추가)
            log.debug("[메인 랭킹 집계 제외] novelId: {} 은(는) 성인물입니다.", novelId);
        }
    }

    // 유료 회차 접근 제어 (공통)
    private void validateEpisodeAccess(Long episodeId, boolean isFree, Long userId) {

        if (isFree) {
            return;
        }

        boolean hasPurchased = pointHistoryRepository
                .existsByUserIdAndEpisodeIdAndType(userId, episodeId, PointHistoryType.NOVEL);

        if (!hasPurchased) {
            throw new ServiceErrorException(EpisodeExceptionEnum.EPISODE_POINT_REQUIRED);
        }
    }



    // [독자용] 성인 열람 권한 확인
    private void validateReaderAdultAccess(Long novelId, UserDetailsImpl userDetails) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ServiceErrorException(NovelExceptionEnum.NOVEL_NOT_FOUND));

        boolean isAdultContent = novel.getTags() != null && novel.getTags().contains(MainTag.ADULT.name());

        if(isAdultContent) {
            if(!userDetails.getUser().isAdultVerificationValid()) {
                throw new ServiceErrorException(UserExceptionEnum.ERR_ADULT_VERIFICATION_REQUIRED);
            }
        }
    }
}