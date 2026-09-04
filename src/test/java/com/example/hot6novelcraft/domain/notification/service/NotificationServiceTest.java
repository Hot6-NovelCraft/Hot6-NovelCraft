package com.example.hot6novelcraft.domain.notification.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NotificationExceptionEnum;
import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import com.example.hot6novelcraft.domain.notification.dto.response.NotificationResponse;
import com.example.hot6novelcraft.domain.notification.entity.Notification;
import com.example.hot6novelcraft.domain.notification.entity.enums.NotificationType;
import com.example.hot6novelcraft.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationService 테스트")
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private static final Long USER_ID = 1L;
    private static final Long NOTIFICATION_ID = 10L;

    private NotificationEvent sampleEvent() {
        return NotificationEvent.pointCharge(USER_ID, 10_000L, 50_000L);
    }

    private Notification mockNotification(Long id, Long userId, boolean isRead) {
        Notification notification = mock(Notification.class);
        given(notification.getId()).willReturn(id);
        given(notification.getUserId()).willReturn(userId);
        given(notification.getType()).willReturn(NotificationType.POINT_CHARGE);
        given(notification.getTitle()).willReturn("포인트 충전 완료");
        given(notification.getContent()).willReturn("10000P 충전 완료");
        given(notification.isRead()).willReturn(isRead);
        return notification;
    }

    // =========================================================
    // consume() 테스트
    // =========================================================
    @Nested
    @DisplayName("consume() - Kafka 이벤트 소비")
    class ConsumeTest {

        @Test
        @DisplayName("첫 수신: DB 저장 후 WebSocket 전송")
        void consume_firstReceived_savesAndSendsWebSocket() {
            // given
            NotificationEvent event = sampleEvent();

            // when
            notificationService.consume(event);

            // then
            verify(notificationRepository, times(1)).save(any(Notification.class));
            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/notifications/" + USER_ID), any(NotificationResponse.class));
        }

        @Test
        @DisplayName("eventId 중복: DB 저장 건너뛰고 WebSocket 재전송")
        void consume_duplicateEventId_skipsInsertAndResendsWebSocket() {
            // given
            NotificationEvent event = sampleEvent();
            SQLIntegrityConstraintViolationException sqlEx =
                    new SQLIntegrityConstraintViolationException("Duplicate entry for key 'event_id'");
            DataIntegrityViolationException dupEx = new DataIntegrityViolationException("event_id", sqlEx);

            given(notificationRepository.save(any())).willThrow(dupEx);
            Notification existing = mockNotification(NOTIFICATION_ID, USER_ID, false);
            given(notificationRepository.findByEventId(event.eventId())).willReturn(Optional.of(existing));

            // when
            notificationService.consume(event);

            // then
            verify(notificationRepository, times(1)).findByEventId(event.eventId());
            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/notifications/" + USER_ID), any(NotificationResponse.class));
        }

        @Test
        @DisplayName("eventId 중복이나 DB 조회 실패: WebSocket 전송 없이 종료")
        void consume_duplicateEventIdButNotFoundInDB_returnsEarly() {
            // given
            NotificationEvent event = sampleEvent();
            SQLIntegrityConstraintViolationException sqlEx =
                    new SQLIntegrityConstraintViolationException("Duplicate entry for key 'event_id'");
            DataIntegrityViolationException dupEx = new DataIntegrityViolationException("event_id", sqlEx);

            given(notificationRepository.save(any())).willThrow(dupEx);
            given(notificationRepository.findByEventId(event.eventId())).willReturn(Optional.empty());

            // when
            notificationService.consume(event);

            // then
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("진짜 DB 오류(event_id 외 필드 위반): 예외 rethrow → Kafka 재소비")
        void consume_realDbError_rethrowsException() {
            // given
            NotificationEvent event = sampleEvent();
            SQLIntegrityConstraintViolationException sqlEx =
                    new SQLIntegrityConstraintViolationException("Column 'user_id' cannot be null");
            DataIntegrityViolationException realEx = new DataIntegrityViolationException("user_id error", sqlEx);

            given(notificationRepository.save(any())).willThrow(realEx);

            // when & then
            assertThatThrownBy(() -> notificationService.consume(event))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("WebSocket 전송 실패: 예외 삼킴 (DB 저장은 완료)")
        void consume_webSocketFails_exceptionSuppressed() {
            // given
            NotificationEvent event = sampleEvent();
            willThrow(new RuntimeException("WebSocket 연결 실패"))
                    .given(messagingTemplate).convertAndSend(anyString(), any(NotificationResponse.class));

            // when - 예외가 밖으로 전파되지 않아야 함
            notificationService.consume(event);

            // then
            verify(notificationRepository, times(1)).save(any(Notification.class));
        }
    }

    // =========================================================
    // getNotifications() 테스트
    // =========================================================
    @Nested
    @DisplayName("getNotifications() - 알림 목록 페이징 조회")
    class GetNotificationsTest {

        @Test
        @DisplayName("성공 - 최신순 페이징 조회")
        void getNotifications_success() {
            // given
            PageRequest pageable = PageRequest.of(0, 20);
            Notification n1 = mockNotification(1L, USER_ID, false);
            Notification n2 = mockNotification(2L, USER_ID, true);
            Page<Notification> page = new PageImpl<>(List.of(n1, n2), pageable, 2);

            given(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable))
                    .willReturn(page);

            // when
            PageResponse<NotificationResponse> result = notificationService.getNotifications(USER_ID, pageable);

            // then
            assertThat(result.content()).hasSize(2);
            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.content().get(0).type()).isEqualTo(NotificationType.POINT_CHARGE);
        }

        @Test
        @DisplayName("성공 - 알림 없을 때 빈 페이지 반환")
        void getNotifications_empty_returnsEmptyPage() {
            // given
            PageRequest pageable = PageRequest.of(0, 20);
            given(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable))
                    .willReturn(Page.empty(pageable));

            // when
            PageResponse<NotificationResponse> result = notificationService.getNotifications(USER_ID, pageable);

            // then
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    // =========================================================
    // getUnreadCount() 테스트
    // =========================================================
    @Nested
    @DisplayName("getUnreadCount() - 읽지 않은 알림 수 조회")
    class GetUnreadCountTest {

        @Test
        @DisplayName("성공 - 읽지 않은 알림 수 반환")
        void getUnreadCount_success() {
            // given
            given(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).willReturn(5L);

            // when
            long count = notificationService.getUnreadCount(USER_ID);

            // then
            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("성공 - 읽지 않은 알림 없을 때 0 반환")
        void getUnreadCount_noUnread_returnsZero() {
            // given
            given(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).willReturn(0L);

            // when
            long count = notificationService.getUnreadCount(USER_ID);

            // then
            assertThat(count).isZero();
        }
    }

    // =========================================================
    // markAsRead() 테스트
    // =========================================================
    @Nested
    @DisplayName("markAsRead() - 개별 읽음 처리")
    class MarkAsReadTest {

        @Test
        @DisplayName("성공 - 읽음 처리 후 응답 반환")
        void markAsRead_success() {
            // given
            Notification notification = mockNotification(NOTIFICATION_ID, USER_ID, false);
            given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

            // when
            NotificationResponse response = notificationService.markAsRead(USER_ID, NOTIFICATION_ID);

            // then
            verify(notification, times(1)).markAsRead();
            assertThat(response.id()).isEqualTo(NOTIFICATION_ID);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 알림 → ERR_NOTIFICATION_NOT_FOUND")
        void markAsRead_notFound_throwsException() {
            // given
            given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(NotificationExceptionEnum.ERR_NOTIFICATION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 다른 유저의 알림 접근 → ERR_NOT_MY_NOTIFICATION")
        void markAsRead_otherUserNotification_throwsException() {
            // given
            Long otherUserId = 999L;
            Notification notification = mockNotification(NOTIFICATION_ID, otherUserId, false);
            given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(NotificationExceptionEnum.ERR_NOT_MY_NOTIFICATION.getMessage());

            verify(notification, never()).markAsRead();
        }
    }

    // =========================================================
    // markAllAsRead() 테스트
    // =========================================================
    @Nested
    @DisplayName("markAllAsRead() - 전체 읽음 처리")
    class MarkAllAsReadTest {

        @Test
        @DisplayName("성공 - 해당 유저의 모든 알림 읽음 처리")
        void markAllAsRead_success() {
            // when
            notificationService.markAllAsRead(USER_ID);

            // then
            verify(notificationRepository, times(1)).markAllAsReadByUserId(USER_ID);
        }
    }
}