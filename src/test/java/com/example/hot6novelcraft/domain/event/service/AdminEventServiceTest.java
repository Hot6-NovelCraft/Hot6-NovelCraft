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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;


@ExtendWith(MockitoExtension.class)
class AdminEventServiceTest {

    @InjectMocks
    private AdminEventService adminEventService;

    @Mock private EventRepository eventRepository;
    @Mock private EventParticipantRepository eventParticipantRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ValueOperations<String, Object> valueOperations;

    private final Long ADMIN_ID = 1L;
    private final Long EVENT_ID = 10L;
    private final LocalDateTime NOW = LocalDateTime.now();
    private final LocalDateTime FUTURE = NOW.plusDays(30);

    private EventCreateRequest validRequest() {
        return new EventCreateRequest(
                "신규 회원 환영 이벤트",
                "선착순 100명에게 포인트를 드립니다",
                5000L,
                100L,
                NOW.minusDays(1),
                FUTURE
        );
    }

    private Event mockEvent() {
        Event event = mock(Event.class);
        Mockito.lenient().when(event.getId()).thenReturn(EVENT_ID);
        Mockito.lenient().when(event.getTitle()).thenReturn("신규 회원 환영 이벤트");
        Mockito.lenient().when(event.getDescription()).thenReturn("선착순 100명에게 포인트를 드립니다");
        Mockito.lenient().when(event.getRewardPoints()).thenReturn(5000L);
        Mockito.lenient().when(event.getMaxParticipants()).thenReturn(100L);
        Mockito.lenient().when(event.getStartedAt()).thenReturn(NOW.minusDays(1));
        Mockito.lenient().when(event.getEndedAt()).thenReturn(FUTURE);
        return event;
    }

    private User mockReader() {
        User user = mock(User.class);
        given(user.getId()).willReturn(2L);
        return user;
    }

    // ===================== createEvent =====================
    @Nested
    @DisplayName("이벤트 생성")
    class CreateEvent {

        @Test
        @DisplayName("정상 생성 - 캐시 evict 및 READER 알림 발송 성공")
        void createEvent_success() {
            Event saved = mockEvent();
            given(eventRepository.save(any())).willReturn(saved);
            given(redisTemplate.keys(any())).willReturn(Set.of());

            Page<User> readerPage = new PageImpl<>(List.of(mockReader()));
            given(userRepository.findAllByRole(eq(UserRole.READER), any(Pageable.class)))
                    .willReturn(readerPage)
                    .willReturn(Page.empty());

            EventDetailResponse response = adminEventService.createEvent(ADMIN_ID, validRequest());

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(EVENT_ID);
            then(eventPublisher).should(atLeastOnce()).publishEvent(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("READER 없을 경우 알림 발송 없이 정상 생성")
        void createEvent_noReaders() {
            Event saved = mockEvent();
            given(eventRepository.save(any())).willReturn(saved);
            given(redisTemplate.keys(any())).willReturn(Set.of());
            given(userRepository.findAllByRole(eq(UserRole.READER), any(Pageable.class)))
                    .willReturn(Page.empty());

            EventDetailResponse response = adminEventService.createEvent(ADMIN_ID, validRequest());

            assertThat(response).isNotNull();
            then(eventPublisher).should(never()).publishEvent(any());
        }
    }

    // ===================== getEventList =====================
    @Nested
    @DisplayName("이벤트 목록 조회 (관리자)")
    class GetEventList {

        @Test
        @DisplayName("ONGOING 상태 필터 조회 성공")
        void getEventList_ongoing() {
            Event event = mockEvent();
            given(event.getStartedAt()).willReturn(NOW.minusDays(1));
            given(event.getEndedAt()).willReturn(FUTURE);
            Page<Event> page = new PageImpl<>(List.of(event));
            given(eventRepository.findAllOngoing(any(), any())).willReturn(page);

            Page<EventSummaryResponse> result = adminEventService.getEventList(
                    EventStatus.ONGOING, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            then(eventRepository).should().findAllOngoing(any(), any());
        }

        @Test
        @DisplayName("ENDED 상태 필터 조회 성공")
        void getEventList_ended() {
            Event event = mockEvent();
            given(event.getStartedAt()).willReturn(NOW.minusDays(30));
            given(event.getEndedAt()).willReturn(NOW.minusDays(1));
            Page<Event> page = new PageImpl<>(List.of(event));
            given(eventRepository.findAllEnded(any(), any())).willReturn(page);

            Page<EventSummaryResponse> result = adminEventService.getEventList(
                    EventStatus.ENDED, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            then(eventRepository).should().findAllEnded(any(), any());
        }

        @Test
        @DisplayName("UPCOMING 상태 필터 조회 성공")
        void getEventList_upcoming() {
            Event event = mockEvent();
            given(event.getStartedAt()).willReturn(FUTURE);
            given(event.getEndedAt()).willReturn(FUTURE.plusDays(7));
            Page<Event> page = new PageImpl<>(List.of(event));
            given(eventRepository.findAllUpcoming(any(), any())).willReturn(page);

            Page<EventSummaryResponse> result = adminEventService.getEventList(
                    EventStatus.UPCOMING, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            then(eventRepository).should().findAllUpcoming(any(), any());
        }

        @Test
        @DisplayName("결과 없을 경우 빈 페이지 반환")
        void getEventList_empty() {
            given(eventRepository.findAllOngoing(any(), any())).willReturn(Page.empty());

            Page<EventSummaryResponse> result = adminEventService.getEventList(
                    EventStatus.ONGOING, PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ===================== getEventDetail =====================
    @Nested
    @DisplayName("이벤트 상세 조회 (관리자)")
    class GetEventDetail {

        @Test
        @DisplayName("정상 조회 성공")
        void getEventDetail_success() {
            Event event = mockEvent();
            given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

            EventDetailResponse response = adminEventService.getEventDetail(EVENT_ID);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(EVENT_ID);
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 - 예외 발생")
        void getEventDetail_notFound() {
            given(eventRepository.findById(EVENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminEventService.getEventDetail(EVENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EventExceptionEnum.EVENT_NOT_FOUND.getMessage());
        }
    }

    // ===================== getParticipants =====================
    @Nested
    @DisplayName("이벤트 참여자 목록 조회")
    class GetParticipants {

        @Test
        @DisplayName("정상 조회 성공")
        void getParticipants_success() {
            EventParticipant participant = mock(EventParticipant.class);
            given(participant.getId()).willReturn(1L);
            given(participant.getUserId()).willReturn(2L);
            given(participant.getJoinedAt()).willReturn(NOW);

            given(eventRepository.existsById(EVENT_ID)).willReturn(true);
            given(eventParticipantRepository.findAllByEventId(eq(EVENT_ID), any()))
                    .willReturn(new PageImpl<>(List.of(participant)));

            Page<EventParticipantResponse> result = adminEventService.getParticipants(
                    EVENT_ID, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).userId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 참여자 조회 - 예외 발생")
        void getParticipants_eventNotFound() {
            given(eventRepository.existsById(EVENT_ID)).willReturn(false);

            assertThatThrownBy(() -> adminEventService.getParticipants(EVENT_ID, PageRequest.of(0, 20)))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(EventExceptionEnum.EVENT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("참여자 없을 경우 빈 페이지 반환")
        void getParticipants_empty() {
            given(eventRepository.existsById(EVENT_ID)).willReturn(true);
            given(eventParticipantRepository.findAllByEventId(eq(EVENT_ID), any()))
                    .willReturn(Page.empty());

            Page<EventParticipantResponse> result = adminEventService.getParticipants(
                    EVENT_ID, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
        }
    }
}