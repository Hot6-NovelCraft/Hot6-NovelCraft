package com.example.hot6novelcraft.domain.event.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EventExceptionEnum;
import com.example.hot6novelcraft.domain.event.dto.response.EventDetailResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventParticipateResponse;
import com.example.hot6novelcraft.domain.event.dto.response.EventSummaryResponse;
import com.example.hot6novelcraft.domain.event.entity.Event;
import com.example.hot6novelcraft.domain.event.entity.EventParticipant;
import com.example.hot6novelcraft.domain.event.entity.enums.EventStatus;
import com.example.hot6novelcraft.domain.event.repository.EventParticipantRepository;
import com.example.hot6novelcraft.domain.event.repository.EventRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class UserEventServiceTest {

    @InjectMocks private UserEventService userEventService;

    @Mock private EventRepository eventRepository;
    @Mock private EventParticipantRepository eventParticipantRepository;
    @Mock private PointService pointService;
    @Mock private RedissonClient redissonClient;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private EventParticipateService eventParticipateService;

    private final Long USER_ID = 1L;
    private final Long EVENT_ID = 10L;
    private final LocalDateTime NOW = LocalDateTime.now();
    private final LocalDateTime FUTURE = NOW.plusDays(30);

    // 실제 Event 객체 생성 (진행 중)
    private Event ongoingEvent() {
        return Event.create(1L, "테스트 이벤트", "설명",
                5000L, 100L, NOW.minusDays(1), FUTURE);
    }

    // 실제 Event 객체 생성 (종료)
    private Event endedEvent() {
        return Event.create(1L, "종료된 이벤트", "설명",
                3000L, 50L, NOW.minusDays(10), NOW.minusDays(1));
    }

    // 락 설정 헬퍼
    private RLock setupLock(boolean acquired) throws InterruptedException {
        RLock mockLock = mock(RLock.class);
        given(redissonClient.getLock(anyString())).willReturn(mockLock);
        given(mockLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(acquired);
        lenient().when(mockLock.isHeldByCurrentThread()).thenReturn(acquired);
        return mockLock;
    }

    // ===================== getEventList =====================
    @Nested
    @DisplayName("이벤트 목록 조회 (사용자)")
    class GetEventList {

        @Test
        @DisplayName("캐시 miss - DB 조회 후 캐싱")
        void getEventList_cacheMiss() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(any())).willReturn(null);
            given(eventRepository.findAllOngoing(any(), any()))
                    .willReturn(new PageImpl<>(List.of(ongoingEvent())));

            Page<EventSummaryResponse> result = userEventService.getEventList(
                    EventStatus.ONGOING, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            then(valueOperations).should().set(any(), any(), any());
        }

        @Test
        @DisplayName("캐시 hit - DB 조회 없이 반환")
        void getEventList_cacheHit() throws Exception {
            Page<EventSummaryResponse> cachedPage = new PageImpl<>(List.of());
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(any())).willReturn(cachedPage);
            given(objectMapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .willReturn(cachedPage);

            Page<EventSummaryResponse> result = userEventService.getEventList(
                    EventStatus.ONGOING, PageRequest.of(0, 10));

            assertThat(result).isNotNull();
            then(eventRepository).should(never()).findAllOngoing(any(), any());
        }

        @Test
        @DisplayName("캐시 역직렬화 실패 시 DB 조회로 전환")
        void getEventList_cacheDeserializeFail() throws Exception {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(any())).willReturn("invalid");
            given(objectMapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .willThrow(new IllegalArgumentException("역직렬화 실패"));
            given(eventRepository.findAllOngoing(any(), any()))
                    .willReturn(new PageImpl<>(List.of(ongoingEvent())));

            Page<EventSummaryResponse> result = userEventService.getEventList(
                    EventStatus.ONGOING, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            then(eventRepository).should().findAllOngoing(any(), any());
        }

        @Test
        @DisplayName("ENDED 상태 필터 조회 성공")
        void getEventList_ended() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(any())).willReturn(null);
            given(eventRepository.findAllEnded(any(), any()))
                    .willReturn(new PageImpl<>(List.of(endedEvent())));

            Page<EventSummaryResponse> result = userEventService.getEventList(
                    EventStatus.ENDED, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            then(eventRepository).should().findAllEnded(any(), any());
        }

        @Test
        @DisplayName("UPCOMING 상태 필터 조회 성공")
        void getEventList_upcoming() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(any())).willReturn(null);
            Event upcoming = Event.create(1L, "예정 이벤트", "설명",
                    2000L, 30L, FUTURE, FUTURE.plusDays(7));
            given(eventRepository.findAllUpcoming(any(), any()))
                    .willReturn(new PageImpl<>(List.of(upcoming)));

            Page<EventSummaryResponse> result = userEventService.getEventList(
                    EventStatus.UPCOMING, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            then(eventRepository).should().findAllUpcoming(any(), any());
        }
    }

    // ===================== getEventDetail =====================
    @Nested
    @DisplayName("이벤트 상세 조회 (사용자)")
    class GetEventDetail {

        @Test
        @DisplayName("캐시 miss - 진행 중 이벤트 DB 조회, 캐싱 안 함")
        void getEventDetail_ongoing_cacheMiss() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(any())).willReturn(null);
            given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(ongoingEvent()));

            EventDetailResponse response = userEventService.getEventDetail(EVENT_ID);

            assertThat(response).isNotNull();
            then(valueOperations).should(never()).set(any(), any(), any());
        }

        @Test
        @DisplayName("캐시 miss - 종료된 이벤트 DB 조회 후 TTL 7일 캐싱")
        void getEventDetail_ended_cacheMissAndStore() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(any())).willReturn(null);
            given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(endedEvent()));

            EventDetailResponse response = userEventService.getEventDetail(EVENT_ID);

            assertThat(response).isNotNull();
            then(valueOperations).should().set(any(), any(), any());
        }

        @Test
        @DisplayName("캐시 hit - DB 조회 없이 반환")
        void getEventDetail_cacheHit() throws Exception {
            EventDetailResponse cached = new EventDetailResponse(
                    EVENT_ID, "테스트", "설명", 5000L, 100L,
                    NOW.minusDays(1), FUTURE, EventStatus.ONGOING);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(any())).willReturn(cached);
            given(objectMapper.convertValue(any(), eq(EventDetailResponse.class))).willReturn(cached);

            EventDetailResponse response = userEventService.getEventDetail(EVENT_ID);

            assertThat(response).isNotNull();
            then(eventRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 - 예외 발생")
        void getEventDetail_notFound() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(any())).willReturn(null);
            given(eventRepository.findById(EVENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userEventService.getEventDetail(EVENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EventExceptionEnum.EVENT_NOT_FOUND.getMessage());
        }
    }

    // ===================== participate =====================
    @Nested
    @DisplayName("이벤트 참여 신청")
    class Participate {

        @Test
        @DisplayName("정상 참여 신청 - 포인트 즉시 지급")
        void participate_success() throws Exception {
            setupLock(true);
            EventParticipateResponse fakeResponse = new EventParticipateResponse(
                    EVENT_ID, USER_ID, 5000L, NOW);
            given(eventParticipateService.execute(EVENT_ID, USER_ID)).willReturn(fakeResponse);

            EventParticipateResponse response = userEventService.participate(EVENT_ID, USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.rewardPoints()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("락 획득 실패 - TOO_MANY_REQUESTS 예외 발생")
        void participate_lockFailed() throws Exception {
            RLock mockLock = mock(RLock.class);
            given(redissonClient.getLock(anyString())).willReturn(mockLock);
            given(mockLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(false);

            assertThatThrownBy(() -> userEventService.participate(EVENT_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EventExceptionEnum.EVENT_LOCK_ACQUIRE_FAILED.getMessage());
        }

        @Test
        @DisplayName("진행 중이 아닌 이벤트 - 예외 발생")
        void participate_notOngoing() throws Exception {
            setupLock(true);
            given(eventParticipateService.execute(EVENT_ID, USER_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_NOT_ONGOING));

            assertThatThrownBy(() -> userEventService.participate(EVENT_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EventExceptionEnum.EVENT_NOT_ONGOING.getMessage());
        }

        @Test
        @DisplayName("중복 참여 시도 - 예외 발생")
        void participate_alreadyParticipated() throws Exception {
            setupLock(true);
            given(eventParticipateService.execute(EVENT_ID, USER_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_ALREADY_PARTICIPATED));

            assertThatThrownBy(() -> userEventService.participate(EVENT_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EventExceptionEnum.EVENT_ALREADY_PARTICIPATED.getMessage());
        }

        @Test
        @DisplayName("선착순 마감 - 예외 발생")
        void participate_participantsFull() throws Exception {
            setupLock(true);
            given(eventParticipateService.execute(EVENT_ID, USER_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_PARTICIPANTS_FULL));

            assertThatThrownBy(() -> userEventService.participate(EVENT_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EventExceptionEnum.EVENT_PARTICIPANTS_FULL.getMessage());
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 - 예외 발생")
        void participate_eventNotFound() throws Exception {
            setupLock(true);
            given(eventParticipateService.execute(EVENT_ID, USER_ID))
                    .willThrow(new ServiceErrorException(EventExceptionEnum.EVENT_NOT_FOUND));

            assertThatThrownBy(() -> userEventService.participate(EVENT_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EventExceptionEnum.EVENT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("락 인터럽트 발생 - 예외 발생")
        void participate_interrupted() throws Exception {
            RLock mockLock = mock(RLock.class);
            given(redissonClient.getLock(anyString())).willReturn(mockLock);
            doThrow(new InterruptedException())
                    .when(mockLock).tryLock(anyLong(), any(TimeUnit.class));

            assertThatThrownBy(() -> userEventService.participate(EVENT_ID, USER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EventExceptionEnum.EVENT_LOCK_ACQUIRE_FAILED.getMessage());
        }
    }
}