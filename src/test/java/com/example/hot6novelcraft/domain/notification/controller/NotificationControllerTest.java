package com.example.hot6novelcraft.domain.notification.controller;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NotificationExceptionEnum;
import com.example.hot6novelcraft.domain.notification.dto.response.NotificationResponse;
import com.example.hot6novelcraft.domain.notification.entity.enums.NotificationType;
import com.example.hot6novelcraft.domain.notification.service.NotificationService;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationController 테스트")
class NotificationControllerTest {

    @InjectMocks
    private NotificationController notificationController;

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Long USER_ID = 1L;
    private static final Long NOTIFICATION_ID = 10L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation
                                .AuthenticationPrincipalArgumentResolver()
                )
                .build();

        User mockUser = mock(User.class);
        given(mockUser.getId()).willReturn(USER_ID);
        given(mockUser.getRole()).willReturn(UserRole.READER);
        given(mockUser.getPassword()).willReturn("password");
        given(mockUser.getEmail()).willReturn("test@test.com");

        UserDetailsImpl userDetails = new UserDetailsImpl(mockUser, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private NotificationResponse sampleResponse(Long id, boolean isRead) {
        return new NotificationResponse(
                id, NotificationType.POINT_CHARGE, "포인트 충전 완료", "10000P 충전 완료",
                null, "POINT", isRead, LocalDateTime.now()
        );
    }

    // =========================================================
    // GET /api/notifications - 알림 목록 조회
    // =========================================================
    @Nested
    @DisplayName("GET /api/notifications - 알림 목록 조회")
    class GetNotificationsTest {

        @Test
        @DisplayName("성공 - 알림 목록 반환")
        void getNotifications_success() throws Exception {
            // given
            List<NotificationResponse> items = List.of(
                    sampleResponse(1L, false),
                    sampleResponse(2L, true)
            );
            PageResponse<NotificationResponse> pageResponse = PageResponse.register(
                    new PageImpl<>(items, PageRequest.of(0, 20), items.size())
            );
            given(notificationService.getNotifications(eq(USER_ID), any())).willReturn(pageResponse);

            // when & then
            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("성공 - page/size 파라미터 적용")
        void getNotifications_withPageParams_success() throws Exception {
            // given
            PageResponse<NotificationResponse> emptyPage = PageResponse.register(
                    new PageImpl<>(List.of(), PageRequest.of(1, 10), 0)
            );
            given(notificationService.getNotifications(eq(USER_ID), any())).willReturn(emptyPage);

            // when & then
            mockMvc.perform(get("/api/notifications")
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("실패 - page=0 입력 시 400 반환 (최솟값 1)")
        void getNotifications_pageZero_returns400() throws Exception {
            mockMvc.perform(get("/api/notifications").param("page", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - size=0 입력 시 400 반환 (최솟값 1)")
        void getNotifications_sizeZero_returns400() throws Exception {
            mockMvc.perform(get("/api/notifications").param("size", "0"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================
    // GET /api/notifications/unread-count - 읽지 않은 수 조회
    // =========================================================
    @Nested
    @DisplayName("GET /api/notifications/unread-count - 읽지 않은 알림 수 조회")
    class GetUnreadCountTest {

        @Test
        @DisplayName("성공 - 읽지 않은 수 반환")
        void getUnreadCount_success() throws Exception {
            // given
            given(notificationService.getUnreadCount(USER_ID)).willReturn(3L);

            // when & then
            mockMvc.perform(get("/api/notifications/unread-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(3));
        }

        @Test
        @DisplayName("성공 - 읽지 않은 알림 없을 때 0 반환")
        void getUnreadCount_noUnread_returnsZero() throws Exception {
            // given
            given(notificationService.getUnreadCount(USER_ID)).willReturn(0L);

            // when & then
            mockMvc.perform(get("/api/notifications/unread-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(0));
        }
    }

    // =========================================================
    // PATCH /api/notifications/{id}/read - 개별 읽음 처리
    // =========================================================
    @Nested
    @DisplayName("PATCH /api/notifications/{id}/read - 개별 읽음 처리")
    class MarkAsReadTest {

        @Test
        @DisplayName("성공 - 읽음 처리 완료")
        void markAsRead_success() throws Exception {
            // given
            NotificationResponse response = sampleResponse(NOTIFICATION_ID, true);
            given(notificationService.markAsRead(USER_ID, NOTIFICATION_ID)).willReturn(response);

            // when & then
            mockMvc.perform(patch("/api/notifications/{id}/read", NOTIFICATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(NOTIFICATION_ID))
                    .andExpect(jsonPath("$.data.isRead").value(true));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 알림 → 404")
        void markAsRead_notFound_returns404() throws Exception {
            // given
            given(notificationService.markAsRead(USER_ID, NOTIFICATION_ID))
                    .willThrow(new ServiceErrorException(NotificationExceptionEnum.ERR_NOTIFICATION_NOT_FOUND));

            // when & then
            mockMvc.perform(patch("/api/notifications/{id}/read", NOTIFICATION_ID))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패 - 다른 유저의 알림 접근 → 403")
        void markAsRead_otherUserNotification_returns403() throws Exception {
            // given
            given(notificationService.markAsRead(USER_ID, NOTIFICATION_ID))
                    .willThrow(new ServiceErrorException(NotificationExceptionEnum.ERR_NOT_MY_NOTIFICATION));

            // when & then
            mockMvc.perform(patch("/api/notifications/{id}/read", NOTIFICATION_ID))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================
    // PATCH /api/notifications/read-all - 전체 읽음 처리
    // =========================================================
    @Nested
    @DisplayName("PATCH /api/notifications/read-all - 전체 읽음 처리")
    class MarkAllAsReadTest {

        @Test
        @DisplayName("성공 - 전체 읽음 처리 완료")
        void markAllAsRead_success() throws Exception {
            // given
            willDoNothing().given(notificationService).markAllAsRead(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/notifications/read-all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("모든 알림 읽음 처리 완료"));
        }
    }
}
