package com.example.hot6novelcraft.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String CACHE_REFRESH_TOKEN_PREFIX = "Refresh:";

    public void saveRefreshToken(String email, String refreshToken, Long durationMillis) {
        String key = CACHE_REFRESH_TOKEN_PREFIX + email;
        redisTemplate.opsForValue().set(key, refreshToken, Duration.ofMillis(durationMillis));
    }

    public String getRefreshToken(String email) {
        String key = CACHE_REFRESH_TOKEN_PREFIX + email;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void deleteRefreshToken(String email) {
        redisTemplate.delete(CACHE_REFRESH_TOKEN_PREFIX + email);
    }
}
