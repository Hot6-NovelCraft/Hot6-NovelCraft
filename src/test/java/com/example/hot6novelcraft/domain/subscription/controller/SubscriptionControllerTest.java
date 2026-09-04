package com.example.hot6novelcraft.domain.subscription.controller;

import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.SubscriptionExceptionEnum;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionCancelRequest;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionCompleteRequest;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionPrepareRequest;
import com.example.hot6novelcraft.domain.subscription.dto.response.SubscriptionPrepareResponse;
import com.example.hot6novelcraft.domain.subscription.dto.response.SubscriptionResponse;
import com.example.hot6novelcraft.domain.subscription.entity.enums.PlanType;
import com.example.hot6novelcraft.domain.subscription.entity.enums.SubscriptionStatus;
import com.example.hot6novelcraft.domain.subscription.service.SubscriptionService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionController 테스트")
class SubscriptionControllerTest {

    @InjectMocks
    private SubscriptionController subscriptionController;

    @Mock
    private SubscriptionService subscriptionService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Long USER_ID = 1L;
    private static final Long SUBSCRIPTION_ID = 100L;
    private static final Long AMOUNT = 9900L;
    private static final String SUBSCRIPTION_KEY = "test-subscription-key-123";
    private static final String BILLING_KEY = "test-billing-key-123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(subscriptionController)
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
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================
    // POST /api/subscriptions/prepare - 구독 준비
    // =========================================================
    @Nested
    @DisplayName("POST /api/subscriptions/prepare - 구독 준비")
    class PrepareSubscriptionTest {

        @Test
        @DisplayName("성공 - 구독 준비 성공")
        void prepareSubscription_success() throws Exception {
            // given
            SubscriptionPrepareRequest request = new SubscriptionPrepareRequest(
                    PlanType.PREMIUM, AMOUNT
            );
            SubscriptionPrepareResponse response = new SubscriptionPrepareResponse(
                    SUBSCRIPTION_KEY, AMOUNT, PlanType.PREMIUM
            );
            given(subscriptionService.prepareSubscription(eq(USER_ID), any(SubscriptionPrepareRequest.class)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(post("/api/subscriptions/prepare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.subscriptionKey").value(SUBSCRIPTION_KEY))
                    .andExpect(jsonPath("$.data.amount").value(AMOUNT))
                    .andExpect(jsonPath("$.data.planType").value("PREMIUM"));

            verify(subscriptionService, times(1))
                    .prepareSubscription(eq(USER_ID), any(SubscriptionPrepareRequest.class));
        }

        @Test
        @DisplayName("실패 - 이미 ACTIVE 구독이 있으면 ERR_ALREADY_SUBSCRIBED")
        void prepareSubscription_alreadySubscribed_fail() throws Exception {
            // given
            SubscriptionPrepareRequest request = new SubscriptionPrepareRequest(
                    PlanType.PREMIUM, AMOUNT
            );
            given(subscriptionService.prepareSubscription(eq(USER_ID), any(SubscriptionPrepareRequest.class)))
                    .willThrow(new ServiceErrorException(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED));

            // when & then
            mockMvc.perform(post("/api/subscriptions/prepare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(SubscriptionExceptionEnum.ERR_ALREADY_SUBSCRIBED.getMessage()));
        }
    }

    // =========================================================
    // POST /api/subscriptions/complete - 구독 완료
    // =========================================================
    @Nested
    @DisplayName("POST /api/subscriptions/complete - 구독 완료")
    class CompleteSubscriptionTest {

        @Test
        @DisplayName("성공 - 구독 완료 성공")
        void completeSubscription_success() throws Exception {
            // given
            SubscriptionCompleteRequest request = new SubscriptionCompleteRequest(
                    SUBSCRIPTION_KEY, BILLING_KEY
            );
            SubscriptionResponse response = new SubscriptionResponse(
                    SUBSCRIPTION_ID,
                    USER_ID,
                    PlanType.PREMIUM,
                    SubscriptionStatus.ACTIVE,
                    AMOUNT,
                    LocalDateTime.now(),
                    LocalDateTime.now().plusMonths(1),
                    null,
                    null
            );
            given(subscriptionService.completeSubscription(eq(USER_ID), any(SubscriptionCompleteRequest.class)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(post("/api/subscriptions/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(SUBSCRIPTION_ID))
                    .andExpect(jsonPath("$.data.userId").value(USER_ID))
                    .andExpect(jsonPath("$.data.planType").value("PREMIUM"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.amount").value(AMOUNT));

            verify(subscriptionService, times(1))
                    .completeSubscription(eq(USER_ID), any(SubscriptionCompleteRequest.class));
        }

        @Test
        @DisplayName("실패 - subscriptionKey가 없으면 ERR_SUBSCRIPTION_KEY_NOT_FOUND")
        void completeSubscription_subscriptionKeyNotFound_fail() throws Exception {
            // given
            SubscriptionCompleteRequest request = new SubscriptionCompleteRequest(
                    SUBSCRIPTION_KEY, BILLING_KEY
            );
            given(subscriptionService.completeSubscription(eq(USER_ID), any(SubscriptionCompleteRequest.class)))
                    .willThrow(new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_KEY_NOT_FOUND));

            // when & then
            mockMvc.perform(post("/api/subscriptions/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_KEY_NOT_FOUND.getMessage()));
        }

        @Test
        @DisplayName("실패 - 첫 결제 실패 시 ERR_FIRST_PAYMENT_FAILED")
        void completeSubscription_firstPaymentFailed_fail() throws Exception {
            // given
            SubscriptionCompleteRequest request = new SubscriptionCompleteRequest(
                    SUBSCRIPTION_KEY, BILLING_KEY
            );
            given(subscriptionService.completeSubscription(eq(USER_ID), any(SubscriptionCompleteRequest.class)))
                    .willThrow(new ServiceErrorException(SubscriptionExceptionEnum.ERR_FIRST_PAYMENT_FAILED));

            // when & then
            mockMvc.perform(post("/api/subscriptions/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(SubscriptionExceptionEnum.ERR_FIRST_PAYMENT_FAILED.getMessage()));
        }
    }

    // =========================================================
    // GET /api/subscriptions/me - 내 구독 조회
    // =========================================================
    @Nested
    @DisplayName("GET /api/subscriptions/me - 내 구독 조회")
    class GetMySubscriptionTest {

        @Test
        @DisplayName("성공 - 내 구독 조회 성공")
        void getMySubscription_success() throws Exception {
            // given
            SubscriptionResponse response = new SubscriptionResponse(
                    SUBSCRIPTION_ID,
                    USER_ID,
                    PlanType.PREMIUM,
                    SubscriptionStatus.ACTIVE,
                    AMOUNT,
                    LocalDateTime.now(),
                    LocalDateTime.now().plusMonths(1),
                    null,
                    null
            );
            given(subscriptionService.getMySubscription(USER_ID))
                    .willReturn(response);

            // when & then
            mockMvc.perform(get("/api/subscriptions/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(SUBSCRIPTION_ID))
                    .andExpect(jsonPath("$.data.userId").value(USER_ID))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(subscriptionService, times(1)).getMySubscription(USER_ID);
        }

        @Test
        @DisplayName("실패 - 구독이 없으면 ERR_SUBSCRIPTION_NOT_FOUND")
        void getMySubscription_notFound_fail() throws Exception {
            // given
            given(subscriptionService.getMySubscription(USER_ID))
                    .willThrow(new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/api/subscriptions/me"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND.getMessage()));
        }
    }

    // =========================================================
    // DELETE /api/subscriptions/{id} - 구독 취소
    // =========================================================
    @Nested
    @DisplayName("DELETE /api/subscriptions/{id} - 구독 취소")
    class CancelSubscriptionTest {

        @Test
        @DisplayName("성공 - 구독 취소 성공")
        void cancelSubscription_success() throws Exception {
            // given
            SubscriptionCancelRequest request = new SubscriptionCancelRequest("서비스 불만족");
            doNothing().when(subscriptionService)
                    .cancelSubscription(eq(USER_ID), eq(SUBSCRIPTION_ID), any(SubscriptionCancelRequest.class));

            // when & then
            mockMvc.perform(delete("/api/subscriptions/{id}", SUBSCRIPTION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(subscriptionService, times(1))
                    .cancelSubscription(eq(USER_ID), eq(SUBSCRIPTION_ID), any(SubscriptionCancelRequest.class));
        }

        @Test
        @DisplayName("실패 - 구독이 없으면 ERR_SUBSCRIPTION_NOT_FOUND")
        void cancelSubscription_notFound_fail() throws Exception {
            // given
            SubscriptionCancelRequest request = new SubscriptionCancelRequest("서비스 불만족");
            doThrow(new ServiceErrorException(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND))
                    .when(subscriptionService)
                    .cancelSubscription(eq(USER_ID), eq(SUBSCRIPTION_ID), any(SubscriptionCancelRequest.class));

            // when & then
            mockMvc.perform(delete("/api/subscriptions/{id}", SUBSCRIPTION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(SubscriptionExceptionEnum.ERR_SUBSCRIPTION_NOT_FOUND.getMessage()));
        }

        @Test
        @DisplayName("실패 - 다른 사용자의 구독이면 ERR_NOT_MY_SUBSCRIPTION")
        void cancelSubscription_notMySubscription_fail() throws Exception {
            // given
            SubscriptionCancelRequest request = new SubscriptionCancelRequest("서비스 불만족");
            doThrow(new ServiceErrorException(SubscriptionExceptionEnum.ERR_NOT_MY_SUBSCRIPTION))
                    .when(subscriptionService)
                    .cancelSubscription(eq(USER_ID), eq(SUBSCRIPTION_ID), any(SubscriptionCancelRequest.class));

            // when & then
            mockMvc.perform(delete("/api/subscriptions/{id}", SUBSCRIPTION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(SubscriptionExceptionEnum.ERR_NOT_MY_SUBSCRIPTION.getMessage()));
        }

        @Test
        @DisplayName("실패 - 이미 취소된 구독이면 ERR_ALREADY_CANCELLED")
        void cancelSubscription_alreadyCancelled_fail() throws Exception {
            // given
            SubscriptionCancelRequest request = new SubscriptionCancelRequest("서비스 불만족");
            doThrow(new ServiceErrorException(SubscriptionExceptionEnum.ERR_ALREADY_CANCELLED))
                    .when(subscriptionService)
                    .cancelSubscription(eq(USER_ID), eq(SUBSCRIPTION_ID), any(SubscriptionCancelRequest.class));

            // when & then
            mockMvc.perform(delete("/api/subscriptions/{id}", SUBSCRIPTION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(SubscriptionExceptionEnum.ERR_ALREADY_CANCELLED.getMessage()));
        }
    }
}
