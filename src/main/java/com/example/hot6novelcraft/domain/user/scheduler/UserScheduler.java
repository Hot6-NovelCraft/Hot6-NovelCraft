package com.example.hot6novelcraft.domain.user.scheduler;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.repository.BlacklistTokenRepository;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserScheduler {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BlacklistTokenRepository blacklistTokenRepository;

    // 매일 01시에 만료된 DB 블랙리스트 토큰 정리 : Redis가 복구되면 DB fallback 데이터도 같이 관리
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void cleanupExpiredBlacklistTokens() {
        blacklistTokenRepository.deleteAllExpiredBefore(LocalDateTime.now());
        log.info("[Blacklist 정리] 만료된 DB 블랙리스트 토큰 삭제 완료");
    }

    // 매일 00시에 탈퇴 회원 정리 실행
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void processExpiredUsers() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        // 30일 지난 "탈퇴 대기" 유저들 조회 (물리적 x)
        List<User> expiredUsers =
                userRepository.findByIsDeletedTrueAndDeletedAtBeforeAndAnonymizedAtIsNull(thirtyDaysAgo);

        if (expiredUsers.isEmpty()) {
            log.info("[회원탈퇴] 오늘 00시 완전 탈퇴 처리할 회원이 없습니다.");
            return;
        }

        // 조회해온 유저들을 하나씩 돌면서 마스킹 처리
        for (User user : expiredUsers) {
            user.anonymize();
            log.info("[회원탈퇴] 회원ID : {}, 비식별화 완료", user.getId());
        }
    }

    // 매일 03시에 Redis 좀비 찌꺼기 키 청소
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupZombieRedisKeys() {
        log.info("[Redis 좀비 청소] 스케쥴러 실행");

        // 과거 버전 잔재로 남아있는 타켓 패턴들 모티브
        String[] targetPatterns = {"SMS:VERIFIED:", "signup:*", "auth:token:*"};

        for (String pattern : targetPatterns) {
            redisTemplate.execute((RedisConnection connection) -> {
                // DB 부하 방지 위해 KEYS * 대신 SCAN 사용 (100개씩)
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100L).build();

                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        String key = new String(cursor.next());
                        Long ttl = redisTemplate.getExpire(key);

                        if (ttl != null && ttl == -1L) {
                            redisTemplate.delete(key);
                            log.info("[Redis 좀비 청소] 삭제된 레디스 찌꺼기 키: {}", key);
                        }
                    }
                } catch (Exception e) {
                    log.error("[Redis 좀비 청소] 키({}) 청소 중 예외 발생", pattern, e);
                }
                return null;
            });
        }
        log.info("[Redis 좀비 청소] 스케쥴러 완료");
    }
}