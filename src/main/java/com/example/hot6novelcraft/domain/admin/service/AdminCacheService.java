package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.admin.repository.CustomAdminRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j(topic = "AdminCacheService")
@Service
@RequiredArgsConstructor
public class AdminCacheService {

    private final RedisUtil redisUtil;
    private final CustomAdminRepositoryImpl customAdminRepository;

    public static final String NEW_USERS_TODAY_KEY = "admin:stats:new_users_today:";
    public static final String NEW_NOVELS_TODAY_KEY = "admin:stats:new_novels_today:";
    public static final String NEW_MENTORS_TODAY_KEY = "admin:stats:new_mentors_today:";

    private static final long CACHE_TTL_SECONDS = 60 * 60 * 48; // 48시간

    // 오늘 신규 가입 회원 수 조회 (캐시 먼저 -> 없으면 DB -> DB 결과 캐시 저장)
    public Long getNewUsersByDate() {
        String key = getTodayUsersKey();
        String cached = redisUtil.getAsString(key);

        if (cached != null) {
            log.info("[Cache HIT] 신규 가입 회원: {}", cached);
            return Long.parseLong(cached);
        }

        log.info("[Cache MISS] 신규 회원 DB 조회");
        Long count = customAdminRepository.countNewUsersToday();

        redisUtil.setWithSeconds(
                key
                , String.valueOf(count)
                , CACHE_TTL_SECONDS
        );
        return count;
    }

    // 오늘 신규 등록 소설 수 조회 (캐시 먼저 -> 없으면 DB -> DB 결과 캐시 저장)
    public Long getNewNovelsByDate() {
        String key = getTodayNovelsKey();
        String cached = redisUtil.getAsString(key);

        if (cached != null) {
            log.info("[Cache HIT] 신작 소설: {}", cached);
            return Long.parseLong(cached);
        }

        log.info("[Cache MISS] 신작 소설 DB 조회");
        Long count = customAdminRepository.countNewNovelsToday();

        redisUtil.setWithSeconds(
                key
                , String.valueOf(count)
                , CACHE_TTL_SECONDS
        );
        return count;
    }

    // 오늘 신규 등록 멘토 수 조회 (캐시 먼저 -> 없으면 DB -> DB 결과 캐시 저장)
    public Long getNewMentosByDate() {
        String key = getTodayMentorsKey();
        String cached = redisUtil.getAsString(key);

        if (cached != null) {
            log.info("[Cache HIT] 신규 멘토 등록: {}", cached);
            return Long.parseLong(cached);
        }

        log.info("[Cache MISS] 신규 멘토 DB 조회");
        Long count = customAdminRepository.countNewMentorsToday();

        redisUtil.setWithSeconds(
                key
                , String.valueOf(count)
                , CACHE_TTL_SECONDS
        );
        return count;
    }

    /**
     * ====== [스케쥴용] 특정 날짜 캐시 직접 조회======
     **/
    public Long getNewUsersByDate(LocalDate targetDate) {
        String key = NEW_USERS_TODAY_KEY + targetDate;
        String cached = redisUtil.getAsString(key);
        return cached != null ? Long.parseLong(cached) : 0L;
    }

    public Long getNewNovelsByDate(LocalDate targetDate) {
        String key = NEW_NOVELS_TODAY_KEY + targetDate;
        String cached = redisUtil.getAsString(key);
        return cached != null ? Long.parseLong(cached) : 0L;
    }

    public Long getNewMentosByDate(LocalDate targetDate) {
        String key = NEW_MENTORS_TODAY_KEY + targetDate;
        String cached = redisUtil.getAsString(key);
        return cached != null ? Long.parseLong(cached) : 0L;
    }

    /**
     * ====== 실시간 데이터 생성 시 카운트 +1 증가 ======
     **/

    // 회원 가입 완료 시 -> Redis 원자적 +1 (동시성)
    public void incrementNewUsersToday() {
        String key = getTodayUsersKey();

        redisUtil.incrementWithSeconds(key, CACHE_TTL_SECONDS);
        log.info("[Cache UPDATE] 신규 회원 가입 +1");
    }

    // 신작 소설 등록 완료 시 -> Redis 원자적 +1 (동시성)
    public void incrementNewNovelsToday() {
        String key = getTodayNovelsKey();

        redisUtil.incrementWithSeconds(key, CACHE_TTL_SECONDS);
        log.info("[Cache UPDATE] 신작 소설 등록 +1");
    }

    // 신규 멘토 등록 완료 시 -> Redis 원자적 +1 (동시성)
    public void incrementNewMentorsToday() {
        String key = getTodayMentorsKey();

        redisUtil.incrementWithSeconds(key, CACHE_TTL_SECONDS);
        log.info("[Cache UPDATE] 신규 멘토 등록 +1");
    }

    /**
     * ====== key 생성 공통 메소드 =======
     */
    // 오늘 날짜가 붙은 key 생성
    private String getTodayUsersKey() {
        return NEW_USERS_TODAY_KEY + LocalDate.now();
    }

    private String getTodayNovelsKey() {
        return NEW_NOVELS_TODAY_KEY + LocalDate.now();
    }

    private String getTodayMentorsKey() {
        return NEW_MENTORS_TODAY_KEY + LocalDate.now();
    }
}
