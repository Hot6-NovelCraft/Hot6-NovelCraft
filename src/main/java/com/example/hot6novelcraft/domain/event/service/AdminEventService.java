package com.example.hot6novelcraft.domain.event.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EventExceptionEnum;
import com.example.hot6novelcraft.domain.event.dto.request.EventCreateRequest;
import com.example.hot6novelcraft.domain.event.dto.response.EventDetailResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventParticipantResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventSummaryResponse;
import com.example.hot6novelcraft.domain.event.entity.Event;
import com.example.hot6novelcraft.domain.event.entity.EventParticipant;
import com.example.hot6novelcraft.domain.event.entity.enums.EventStatus;
import com.example.hot6novelcraft.domain.event.repository.EventParticipantRepository;
import com.example.hot6novelcraft.domain.event.repository.EventRepository;
import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminEventService {

    private static final String EVENT_LIST_CACHE_KEY = "event:list:";

    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;


    @Transactional
    public EventDetailResponse createEvent(Long adminId, EventCreateRequest request) {
        if (!request.startedAt().isBefore(request.endedAt())) {
            throw new ServiceErrorException(EventExceptionEnum.EVENT_INVALID_PERIOD);
        }

        Event event = Event.create(
                adminId,
                request.title(),
                request.description(),
                request.rewardPoints(),
                request.maxParticipants(),
                request.startedAt(),
                request.endedAt()
        );

        Event saved = eventRepository.save(event);

        // 사용자 목록 캐시 evict
        evictEventListCache();

        int page = 0;
        int batchSize = 1000;
        while (true) {
            PageRequest pageRequest = PageRequest.of(page, batchSize);
            List<User> readers = userRepository.findAllByRole(UserRole.READER, pageRequest).getContent();
            if (readers.isEmpty()) break;
            readers.forEach(user -> eventPublisher.publishEvent(
                    NotificationEvent.eventCreated(user.getId(), saved.getTitle(), saved.getId())
            ));
            page++;
        }

        return EventDetailResponse.from(saved);
    }

    // 실시간 조회 (캐싱 없음)
    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> getEventList(EventStatus status, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Page<Event> events = switch (status) {
            case UPCOMING -> eventRepository.findAllUpcoming(now, pageable);
            case ONGOING -> eventRepository.findAllOngoing(now, pageable);
            case ENDED -> eventRepository.findAllEnded(now, pageable);
        };
        return events.map(e -> EventSummaryResponse.from(e, resolveStatus(e)));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ServiceErrorException(EventExceptionEnum.EVENT_NOT_FOUND));
        return EventDetailResponse.from(event);
    }

    @Transactional(readOnly = true)
    public Page<EventParticipantResponse> getParticipants(Long eventId, Pageable pageable) {
        if (!eventRepository.existsById(eventId)) {
            throw new ServiceErrorException(EventExceptionEnum.EVENT_NOT_FOUND);
        }
        Page<EventParticipant> participants = eventParticipantRepository.findAllByEventId(eventId, pageable);
        return participants.map(EventParticipantResponse::from);
    }

    private EventStatus resolveStatus(Event event) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getStartedAt())) return EventStatus.UPCOMING;
        if (now.isAfter(event.getEndedAt())) return EventStatus.ENDED;
        return EventStatus.ONGOING;
    }

    private void evictEventListCache() {
        for (EventStatus status : EventStatus.values()) {
            Set<String> keys = redisTemplate.keys(EVENT_LIST_CACHE_KEY + status + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }
}