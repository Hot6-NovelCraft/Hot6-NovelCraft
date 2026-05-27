package com.example.hot6novelcraft.domain.event.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EventExceptionEnum;
import com.example.hot6novelcraft.domain.event.dto.response.EventDetailResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventParticipateResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventSummaryResponse;
import com.example.hot6novelcraft.domain.event.entity.Event;
import com.example.hot6novelcraft.domain.event.entity.enums.EventStatus;
import com.example.hot6novelcraft.domain.event.repository.EventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventService {

    private static final String EVENT_LIST_CACHE_KEY = "event:list:";
    private static final String EVENT_DETAIL_CACHE_KEY = "event:detail:";
    private static final String EVENT_LOCK_KEY = "lock:event:participate:";

    private final EventRepository eventRepository;
    private final EventParticipateService eventParticipateService;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // ===================== 이벤트 목록 조회 (캐싱 O) =====================

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> getEventList(EventStatus status, Pageable pageable) {
        String cacheKey = EVENT_LIST_CACHE_KEY + status
                + ":" + pageable.getPageNumber()
                + ":" + pageable.getPageSize()
                + ":" + pageable.getSort();

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.convertValue(cached, new TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("이벤트 목록 캐시 역직렬화에 실패하여 DB 조회로 전환합니다. key={}", cacheKey);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Page<Event> events = switch (status) {
            case UPCOMING -> eventRepository.findAllUpcoming(now, pageable);
            case ONGOING -> eventRepository.findAllOngoing(now, pageable);
            case ENDED -> eventRepository.findAllEnded(now, pageable);
        };

        Page<EventSummaryResponse> result = events.map(e -> EventSummaryResponse.from(e, status));
        redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(5));

        return result;
    }

    // ===================== 이벤트 상세 조회 =====================

    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(Long eventId) {
        String cacheKey = EVENT_DETAIL_CACHE_KEY + eventId;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.convertValue(cached, EventDetailResponse.class);
            } catch (Exception e) {
                log.warn("이벤트 상세 캐시 역직렬화에 실패하여 DB 조회로 전환합니다. eventId={}", eventId);
            }
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ServiceErrorException(EventExceptionEnum.EVENT_NOT_FOUND));

        EventDetailResponse response = EventDetailResponse.from(event);

        if (event.isEnded()) {
            redisTemplate.opsForValue().set(cacheKey, response, Duration.ofDays(7));
        }

        return response;
    }

    // ===================== 이벤트 참여 신청 (Redisson 분산락) =====================

    public EventParticipateResponse participate(Long eventId, Long userId) {
        RLock lock = redissonClient.getLock(EVENT_LOCK_KEY + eventId);

        try {
            boolean acquired = lock.tryLock(5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new ServiceErrorException(EventExceptionEnum.EVENT_LOCK_ACQUIRE_FAILED);
            }

            // 트랜잭션이 커밋된 후에 락이 해제되도록 별도 서비스에서 실행
            return eventParticipateService.execute(eventId, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceErrorException(EventExceptionEnum.EVENT_LOCK_ACQUIRE_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}