package com.example.hot6novelcraft.domain.payment.controller;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentCancelRequest;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentPrepareRequest;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentHistoryResponse;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentPrepareResponse;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentResponse;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentMethod;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.payment.service.PaymentService;
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentController 테스트")
class PaymentControllerTest {

    @InjectMocks
    private PaymentController paymentController;

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Long USER_ID = 1L;
    private static final Long PAYMENT_ID = 100L;
    private static final Long AMOUNT = 10000L;
    private static final String PAYMENT_KEY = "test-payment-key-123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
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
    // 결제 내역 조회 테스트
    // =========================================================
    @Nested
    @DisplayName("GET /api/payments - 결제 내역 조회")
    class GetPaymentHistory {

        @Test
        @DisplayName("성공 - 결제 내역 조회")
        void getPaymentHistory_success() throws Exception {
            // given
            List<PaymentHistoryResponse> historyList = List.of(
                    new PaymentHistoryResponse(
                            1L, "payment-key-1", 10000L, PaymentMethod.CARD.name(),
                            PaymentStatus.COMPLETED.name(), LocalDateTime.now(), null
                    ),
                    new PaymentHistoryResponse(
                            2L, "payment-key-2", 20000L, PaymentMethod.CARD.name(),
                            PaymentStatus.COMPLETED.name(), LocalDateTime.now(), null
                    )
            );

            PageResponse<PaymentHistoryResponse> mockResponse = PageResponse.register(
                    new PageImpl<>(historyList, PageRequest.of(0, 10), historyList.size())
            );

            given(paymentService.getPaymentHistory(eq(USER_ID), any()))
                    .willReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/payments")
                            .param("page", "1")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.message").value("결제 내역 조회 성공"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(2));
        }

        @Test
        @DisplayName("성공 - 결제 내역이 없을 경우 빈 배열 반환")
        void getPaymentHistory_empty() throws Exception {
            // given
            PageResponse<PaymentHistoryResponse> mockResponse = PageResponse.register(
                    new PageImpl<>(List.of(), PageRequest.of(0, 10), 0)
            );

            given(paymentService.getPaymentHistory(eq(USER_ID), any()))
                    .willReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/payments")
                            .param("page", "1")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(0));
        }

        @Test
        @DisplayName("실패 - page 파라미터가 0 이하")
        void getPaymentHistory_invalidPage() throws Exception {
            // when & then
            mockMvc.perform(get("/api/payments")
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @org.junit.jupiter.api.Disabled("standaloneSetup에서는 파라미터 검증이 작동하지 않음")
        @DisplayName("실패 - size 파라미터가 100 초과")
        void getPaymentHistory_invalidSize() throws Exception {
            // when & then
            mockMvc.perform(get("/api/payments")
                            .param("page", "1")
                            .param("size", "101")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================
    // 결제 준비 테스트
    // =========================================================
    @Nested
    @DisplayName("POST /api/payments/prepare - 결제 준비")
    class PreparePayment {

        @Test
        @DisplayName("성공 - 결제 준비")
        void preparePayment_success() throws Exception {
            // given
            PaymentPrepareRequest request = new PaymentPrepareRequest(AMOUNT);
            PaymentPrepareResponse mockResponse = new PaymentPrepareResponse(PAYMENT_KEY, AMOUNT);

            given(paymentService.preparePayment(USER_ID, AMOUNT))
                    .willReturn(mockResponse);

            // when & then
            mockMvc.perform(post("/api/payments/prepare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.message").value("결제 준비가 완료되었습니다"))
                    .andExpect(jsonPath("$.data.paymentKey").value(PAYMENT_KEY))
                    .andExpect(jsonPath("$.data.amount").value(AMOUNT));
        }

        @Test
        @DisplayName("실패 - amount가 null")
        void preparePayment_nullAmount() throws Exception {
            // given
            String invalidRequest = "{}";

            // when & then
            mockMvc.perform(post("/api/payments/prepare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - amount가 음수")
        void preparePayment_negativeAmount() throws Exception {
            // given
            PaymentPrepareRequest request = new PaymentPrepareRequest(-1000L);

            // when & then
            mockMvc.perform(post("/api/payments/prepare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================
    // 결제 확인 테스트
    // =========================================================
    @Nested
    @DisplayName("POST /api/payments/confirm - 결제 확인")
    class ConfirmPayment {

        @Test
        @DisplayName("성공 - 결제 확인 및 포인트 충전")
        void confirmPayment_success() throws Exception {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            PaymentResponse mockResponse = new PaymentResponse(
                    PAYMENT_ID, AMOUNT, PaymentStatus.COMPLETED.name(), java.time.LocalDateTime.now()
            );

            given(paymentService.confirmPayment(USER_ID, request))
                    .willReturn(mockResponse);

            // when & then
            mockMvc.perform(post("/api/payments/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.message").value("결제가 완료되었습니다"))
                    .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                    .andExpect(jsonPath("$.data.amount").value(AMOUNT))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("실패 - paymentKey가 null")
        void confirmPayment_nullPaymentKey() throws Exception {
            // given
            String invalidRequest = "{\"amount\": 10000}";

            // when & then
            mockMvc.perform(post("/api/payments/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 결제 처리 중 (락 획득 실패)")
        void confirmPayment_paymentProcessing() throws Exception {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            given(paymentService.confirmPayment(USER_ID, request))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING));

            // when & then
            mockMvc.perform(post("/api/payments/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage()));
        }

        @Test
        @DisplayName("실패 - 금액 불일치")
        void confirmPayment_amountMismatch() throws Exception {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            given(paymentService.confirmPayment(USER_ID, request))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_AMOUNT_MISMATCH));

            // when & then
            mockMvc.perform(post("/api/payments/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_AMOUNT_MISMATCH.getMessage()));
        }

        @Test
        @DisplayName("실패 - PortOne API 오류")
        void confirmPayment_portOneError() throws Exception {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            given(paymentService.confirmPayment(USER_ID, request))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PORTONE_API_ERROR));

            // when & then
            mockMvc.perform(post("/api/payments/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_PORTONE_API_ERROR.getMessage()));
        }
    }

    // =========================================================
    // 환불 테스트
    // =========================================================
    @Nested
    @DisplayName("POST /api/payments/{paymentId}/cancel - 환불")
    class CancelPayment {

        @Test
        @DisplayName("성공 - 환불 처리")
        void cancelPayment_success() throws Exception {
            // given
            PaymentCancelRequest request = new PaymentCancelRequest("단순 변심");
            PaymentResponse mockResponse = new PaymentResponse(
                    PAYMENT_ID, AMOUNT, PaymentStatus.REFUNDED.name(), java.time.LocalDateTime.now()
            );

            given(paymentService.cancelPayment(USER_ID, PAYMENT_ID, "단순 변심"))
                    .willReturn(mockResponse);

            // when & then
            mockMvc.perform(post("/api/payments/{paymentId}/cancel", PAYMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.message").value("환불이 완료되었습니다"))
                    .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                    .andExpect(jsonPath("$.data.status").value("REFUNDED"));
        }

        @Test
        @DisplayName("실패 - reason이 null")
        void cancelPayment_nullReason() throws Exception {
            // given
            String invalidRequest = "{}";

            // when & then
            mockMvc.perform(post("/api/payments/{paymentId}/cancel", PAYMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - Payment를 찾을 수 없음")
        void cancelPayment_paymentNotFound() throws Exception {
            // given
            PaymentCancelRequest request = new PaymentCancelRequest("단순 변심");
            given(paymentService.cancelPayment(USER_ID, PAYMENT_ID, "단순 변심"))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND));

            // when & then
            mockMvc.perform(post("/api/payments/{paymentId}/cancel", PAYMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND.getMessage()));
        }

        @Test
        @DisplayName("실패 - 이미 환불 처리 중")
        void cancelPayment_alreadyCanceling() throws Exception {
            // given
            PaymentCancelRequest request = new PaymentCancelRequest("단순 변심");
            given(paymentService.cancelPayment(USER_ID, PAYMENT_ID, "단순 변심"))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_CANCELING));

            // when & then
            mockMvc.perform(post("/api/payments/{paymentId}/cancel", PAYMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_CANCELING.getMessage()));
        }

        @Test
        @DisplayName("실패 - 환불 불가능한 상태")
        void cancelPayment_cannotCancel() throws Exception {
            // given
            PaymentCancelRequest request = new PaymentCancelRequest("단순 변심");
            given(paymentService.cancelPayment(USER_ID, PAYMENT_ID, "단순 변심"))
                    .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_CANNOT_CANCEL));

            // when & then
            mockMvc.perform(post("/api/payments/{paymentId}/cancel", PAYMENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value(PaymentExceptionEnum.ERR_PAYMENT_CANNOT_CANCEL.getMessage()));
        }
    }
}
