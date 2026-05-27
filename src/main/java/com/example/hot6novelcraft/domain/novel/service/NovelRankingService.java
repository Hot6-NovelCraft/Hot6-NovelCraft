package com.example.hot6novelcraft.domain.novel.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NovelExceptionEnum;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelRankingResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NovelRankingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NovelRepository novelRepository;
    private final UserRepository userRepository;

    public static final String REALTIME_RANKING_KEY = "ranking:novel:realtime";
    public static final String WEEKLY_RANKING_KEY = "ranking:novel:weekly";

    // 인기 소설 랭킹 조회 (실시간/주간) - 장애 격리 적용
    public List<NovelRankingResponse> getNovelRanking(String type) {
        String redisKey;

        if ("realtime".equalsIgnoreCase(type)) {
            redisKey = REALTIME_RANKING_KEY;
        } else if ("weekly".equalsIgnoreCase(type)) {
            redisKey = WEEKLY_RANKING_KEY;
        } else {
            throw new ServiceErrorException(NovelExceptionEnum.INVALID_RANKING_TYPE);
        }

        try {
            // 정상 : Redis에서 조회 시도
            return buildRankingFromRedis(redisKey, 5);

        } catch (Exception e) {
            // 장애 : Redis 연결 실패 시 DB 쿼리로 우회
            log.error("[Redis 장애 발생] 랭킹 조회를 DB Fallback으로 전환합니다. type: {}, error: {}", type, e.getMessage());
            return buildRankingFromDBFallback(type, 5);
        }
    }

    // 인기 소설 랭킹 조회
    public List<NovelRankingResponse> buildRankingFromRedis(String redisKey, int limit) {

        // ZSet에서 Score(조회수) 기준 내림차순 TOP 5 조회
        Set<ZSetOperations.TypedTuple<Object>> topNovels
                = redisTemplate.opsForZSet().reverseRangeWithScores(redisKey, 0, limit - 1);

        // Redis 데이터에 없으면 빈 리스트 반환
        if (topNovels == null || topNovels.isEmpty()) {
            return new ArrayList<>();
        }

        // novelId 리스트 추출 -> IN 절로 한번에 조회
        List<Long> novelIds = topNovels
                .stream()
                .map(tuple -> Long.valueOf(tuple.getValue().toString()))
                .toList();

        // DB에서 novelId를 IN 으로 한번에 조회
        Map<Long, Novel> novelMap = novelRepository.findAllById(novelIds)
                .stream()
                .collect(Collectors.toMap(Novel::getId, novel -> novel));

        // 작가 ID 목록 추출해서 한번에 조회
        List<Long> authorIds = novelMap.values()
                .stream()
                .map(Novel::getAuthorId)
                .distinct()
                .toList();

        Map<Long, String> authorNicknameMap = userRepository.findAllById(authorIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        // 랭킹 응답 조회
        List<NovelRankingResponse> rankingList = new ArrayList<>();

        int rank = 1;

        for (ZSetOperations.TypedTuple<Object> tuple : topNovels) {
            Long novelId = Long.valueOf(tuple.getValue().toString());
            Long viewCount = tuple.getScore().longValue();
            Novel novel = novelMap.get(novelId);

            // 삭제되었거나 데이터베이스에 없는 소설 체크
            if (novel == null || novel.isDeleted()) {
                continue;
            }

            rankingList.add(NovelRankingResponse.of(
                    rank++
                    , novelId
                    , novel.getTitle()
                    , authorNicknameMap.getOrDefault(novel.getAuthorId(), "알 수 없음")
                    , viewCount
                    , novel.getCoverImageUrl()
            ));
        }
        return rankingList;
    }

    // Redis 장애 Fallback 호출
    private List<NovelRankingResponse> buildRankingFromDBFallback(String type, int limit) {

        List<Novel> topNovels;

        // 타입에 따라 DB에서 직접 집계해서 가져오기 (QueryDSL 활용)
        if ("realtime".equalsIgnoreCase(type)) {
            topNovels = novelRepository.findHourlyTopNovels(limit);
        } else {
            topNovels = novelRepository.findWeeklyTopNovels(limit);
        }

        if (topNovels.isEmpty()) {
            return new ArrayList<>();
        }

        // 작가 닉네임 매핑
        List<Long> authorIds = topNovels.stream()
                .map(Novel::getAuthorId)
                .distinct()
                .toList();

        Map<Long, String> authorNicknameMap = userRepository.findAllById(authorIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        // response 필터링 및 적용
        List<NovelRankingResponse> rankingList = new ArrayList<>();

        int rank = 1;

        for (Novel novel : topNovels) {
            if (novel.getStatus() == NovelStatus.PENDING || novel.getStatus() == NovelStatus.HIATUS) {
                continue;
            }
            if (novel.getTags() != null && (novel.getTags().contains("성인") || novel.getTags().contains("19금"))) {
                continue;
            }
            rankingList.add(NovelRankingResponse.of(
                    rank++
                    , novel.getId()
                    , novel.getTitle()
                    , authorNicknameMap.getOrDefault(novel.getAuthorId(), "알 수 없음")
                    , novel.getViewCount()
                    , novel.getCoverImageUrl()
            ));
        }
        return rankingList;
    }
}