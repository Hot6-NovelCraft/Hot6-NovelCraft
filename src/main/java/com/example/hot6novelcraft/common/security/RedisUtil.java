package com.example.hot6novelcraft.common.security;

import com.example.hot6novelcraft.domain.user.entity.BlacklistToken;
import com.example.hot6novelcraft.domain.user.repository.BlacklistTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final BlacklistTokenRepository blacklistTokenRepository;

    // NOTE: 이 클래스의 모든 Redis 호출은 catch(Exception)으로 통일한다.
    // 이유: Lettuce/Redisson 혼용 환경에서 실제 장애 시 RedisConnectionFailureException뿐 아니라
    // RedisSystemException(형제 클래스, 상속 관계 아님) 등 다양한 타입이 던져지는 것이 실측으로 확인됨
    // (fail-policy 테스트 중 RedisSystemException으로 인해 좁은 catch가 무력화되어
    //  Sentinel failover 구간(최대 16초) 동안 요청이 그대로 hang되는 문제 발견).
    // 이 필드들의 관심사는 "예외 종류 구분"이 아니라 "Redis 가용 여부에 따른 Fallback 실행"이므로
    // 넓게 잡아도 안전하다.

    /**
     * === 블랙리스트 등록 - Failover 중 쓰기 실패 시 로그만 남김 ===
     **/
    public void setBlackList(String accessToken, Object object, Duration duration) {
        try {
            redisTemplate.opsForValue().set(accessToken, object, duration);
        } catch (Exception e) {
            log.error("[Redis Blacklist] 등록 실패, accessToken: {}, reason: {}", accessToken, e.getMessage(), e);

            try {    // DB fallback : Redis 장애 중에도 로그아웃 토큰 차단 유지
                if (!blacklistTokenRepository.existsByToken(accessToken)) {
                    LocalDateTime expirationTime = LocalDateTime.now().plus(duration);
                    String reason = (object != null) ? object.toString() : "Unknown";
                    blacklistTokenRepository.save(BlacklistToken.of(accessToken, reason, expirationTime));
                    log.warn("[Redis Blacklist] DB에 블랙리스트 저장 완료, expiredAt: {}", expirationTime);
                }
            } catch (Exception dbEx) {
                log.error("[Redis Blacklist] DB fallback 저장 실패, reason: {}", dbEx.getMessage(), dbEx);
            }
        }
    }

    // 블랙리스트 조회 실패 시 블랙리스트가 아닌 것으로 처리
    public boolean isBlackList(String accessToken) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(accessToken));
        } catch (Exception e) {
            log.error("[Redis Blacklist] 조회 실패, DB fallback 실행, accessToken: {}, reason: {}", accessToken, e.getMessage());
            return blacklistTokenRepository.existsByToken(accessToken);
        }
    }

    /**
     * === SMS 서비스 - 원자적 getAndDelete ===
     **/
    public Object getAndDelete(String signupKey) {
        try {
            return redisTemplate.opsForValue().getAndDelete(signupKey);
        } catch (Exception e) {
            log.error("[Redis-Signup] getAndDelete 실패, key: {}, reason: {}", signupKey, e.getMessage());
            return null; // 호출부에서 null 체크 후 인증 만료 처리
        }
    }

    /**
     * === SMS 서비스 - 원자적 검증 (인증번호 저장, 재사용 방지) 및 삭제 ===
     **/
    public boolean verifyAndDeleteWithLua(String key, String expectedValue) {
        String script =
                "if redis.call('get', KEYS[1]) == ARGV[1] then \n" +
                        "   redis.call('del', KEYS[1]) \n" +
                        "   return 1 \n" +
                        "else \n" +
                        "   return 0 \n" +
                        "end";
        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

            // 스크립트 실행
            Long result = redisTemplate.execute(redisScript, Collections.singletonList(key), expectedValue);

            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("[Redis-SMS] Lua 검증 실패, key: {}, reason: {}", key, e.getMessage());
            return false; // 인증 실패 -> 사용자에게 재시도 안내
        }
    }

    // 데이터 조회 = Failover 시 null 반환, 호출부에서 DB Fallback 처리
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[Redis] 조회 실패, key: {}, reason: {} ,fallback 실행", key, e.getMessage());
            return null;
        }
    }

    // 데이터 삭제 - 인증 성공 후 바로 삭제
    public boolean delete(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.error("[Redis Delete] 삭제 실패, key: {}, reason: {}", key, e.getMessage());
            return false;
        }
    }

    // 데이터 저장 (분 단위 TTL) - 쓰기 실패는 무시하고 진행
    public void set(String key, Object value, long duration) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(duration));
        } catch (Exception e) {
            log.error("[Redis set] 저장 실패, key: {}, reason: {}", key, e.getMessage());
        }
    }

    // SMS 일일 전송 제한 - Lua INCR + EXPIRE
    public Long incrementAndExpire(String key, long durationMinutes) {

        String script =
                "local count = redis.call('INCR', KEYS[1]) \n" +
                        "if tonumber(count) == 1 then \n" +
                        "   redis.call('EXPIRE', KEYS[1], ARGV[1]) \n" +
                        "end \n" +
                        "return count";

        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

            // durationMinutes * 60을 통해 초 단위로 변환해 넘겨줌
            return redisTemplate.execute(redisScript, Collections.singletonList(key), durationMinutes * 60);
        } catch (Exception e) {
            log.error("[Redis INCR] incrementAndExpire 실패, key: {}, reason: {}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * Redis 분산 락 획득 (Redisson RLock).
     * tryLock()은 대기 없이 즉시 시도하며, 획득 성공 시 워치독이 TTL을 자동 갱신한다.
     * 워치독 덕분에 작업 시간이 길어져도 락이 만료되지 않는다.
     *
     * @return true면 락 획득 성공, false면 이미 다른 프로세스가 보유 중
     */
    public boolean acquireLock(String key) {
        try {
            boolean acquired = redissonClient.getLock(key).tryLock();
            if (acquired) {
                log.info("[RedisLock] 락 획득 key={}", key);
            } else {
                log.warn("[RedisLock] 락 획득 실패 (이미 점유 중) key={}", key);
            }
            return acquired;
        } catch (Exception e) {
            log.error("[RedisLock] 락 획득 중 예외, key={}, reason={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Redis 분산 락 해제.
     * 현재 스레드가 보유한 락만 해제한다 (다른 스레드의 락은 건드리지 않음).
     */
    public void releaseLock(String key) {
        try {
            RLock lock = redissonClient.getLock(key);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[RedisLock] 락 해제 key={}", key);
            } else {
                log.warn("[RedisLock] 락 해제 스킵 (현재 스레드가 소유하지 않음) key={}", key);
            }
        } catch (Exception e) {
            log.error("[RedisLock] 락 해제, key={}, reason={}", key, e.getMessage());
        }
    }

    /**
     * == 대시보드 통계용 - setWithSeconds 으로 초단위 업데이트 설정 (초 단위 TTL) ==
     */
    public void setWithSeconds(String key, Object value, long seconds) {

        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {

                StringRedisSerializer serializer = new StringRedisSerializer();
                byte[] rawKey = serializer.serialize(key);
                byte[] rawValue = serializer.serialize(value.toString());

                // Redis setEx 명령어로 TTL이랑 저장
                connection.setEx(rawKey, seconds, rawValue);
                return null;
            });
        } catch (Exception e) {
            log.error("[Redis SetEx] setWithSeconds 실패, key: {}, reason: {}", key, e.getMessage());
        }
    }

    // 캐시 값 문자열로 파싱
    public String getAsString(String key) {

        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("[Redis GetStr] 조회 실패, key: {}, reason: {}", key, e.getMessage());
            return null;
        }
    }

    // 대시보드 통계 - Redis INCR + 1 (초 단위 동시성 제어), 키가 없으면 1로 초기화하고 TTL 설정
    public long incrementWithSeconds(String key, long durationSeconds) {
        StringRedisSerializer serializer = new StringRedisSerializer();

        try {
            Long result = redisTemplate.execute((RedisCallback<Long>) connection -> {
                byte[] rawKey = serializer.serialize(key);

                String script =
                        "local count = redis.call('INCR', KEYS[1]) \n" +
                                "if tonumber(count) == 1 then \n" +
                                "   redis.call('EXPIRE', KEYS[1], ARGV[1]) \n" +
                                "end \n" +
                                "return count";

                byte[] rawArg = serializer.serialize(String.valueOf(durationSeconds));

                return connection.eval(
                        script.getBytes()
                        , ReturnType.INTEGER
                        , 1
                        , rawKey
                        , rawArg
                );
            });
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.error("[Redis IncrEx] incrementWithSeconds 실패, key: {}, reason: {}", key, e.getMessage());
            return 0L;
        }
    }
}