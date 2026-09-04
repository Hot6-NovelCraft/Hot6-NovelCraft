package com.example.hot6novelcraft.domain.payment.controller;

import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.domain.payment.dto.request.WebhookRequest;
import com.example.hot6novelcraft.domain.payment.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebhookController 테스트")
class WebhookControllerTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private WebhookVerifier webhookVerifier;

    private WebhookController webhookController;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String WEBHOOK_ENDPOINT = "/api/webhooks/portone";
    private static final String WEBHOOK_ID = "webhook-id-12345";
    private static final String WEBHOOK_SIGNATURE = "signature-abcdef";
    private static final String WEBHOOK_TIMESTAMP = "1234567890";
    private static final String PAYMENT_ID = "payment-test-key-12345";
    private static final String TRANSACTION_ID = "transaction-12345";

    @BeforeEach
    void setUp() {
        webhookController = new WebhookController(webhookService, webhookVerifier, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(webhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String createWebhookRequestBody(String type, String paymentId, String transactionId) throws Exception {
        WebhookRequest.WebhookData data = new WebhookRequest.WebhookData(paymentId, transactionId, null);
        WebhookRequest request = new WebhookRequest(type, data);
        return objectMapper.writeValueAsString(request);
    }

    // =========================================================
    // handlePortOneWebhook() - 웹훅 수신 처리
    // =========================================================
    @Nested
    @DisplayName("POST /api/webhooks/portone - 웹훅 수신")
    class HandlePortOneWebhookTest {

        @Test
        @DisplayName("성공 - Transaction.Paid 웹훅 처리")
        void handlePortOneWebhook_paid_success() throws Exception {
            // given
            String requestBody = createWebhookRequestBody("Transaction.Paid", PAYMENT_ID, TRANSACTION_ID);
            // WebhookVerifier와 WebhookService는 @Mock이므로 기본적으로 아무것도 하지 않음

            // when & then
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, WEBHOOK_SIGNATURE)
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(webhookVerifier, times(1)).verify(requestBody, WEBHOOK_ID, WEBHOOK_SIGNATURE, WEBHOOK_TIMESTAMP);
            verify(webhookService, times(1)).handleWebhook(any(WebhookRequest.class));
        }

        @Test
        @DisplayName("성공 - Transaction.Failed 웹훅 처리")
        void handlePortOneWebhook_failed_success() throws Exception {
            // given
            String requestBody = createWebhookRequestBody("Transaction.Failed", PAYMENT_ID, TRANSACTION_ID);

            // when & then
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, WEBHOOK_SIGNATURE)
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(webhookService, times(1)).handleWebhook(any(WebhookRequest.class));
        }

        @Test
        @DisplayName("성공 - Transaction.Cancelled 웹훅 처리")
        void handlePortOneWebhook_cancelled_success() throws Exception {
            // given
            String requestBody = createWebhookRequestBody("Transaction.Cancelled", PAYMENT_ID, TRANSACTION_ID);

            // when & then
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, WEBHOOK_SIGNATURE)
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(webhookService, times(1)).handleWebhook(any(WebhookRequest.class));
        }

        @Test
        @DisplayName("서명 검증 실패 - 200 OK 반환 (재시도 방지)")
        void handlePortOneWebhook_verificationFailed_returns200() throws Exception {
            // given
            String requestBody = createWebhookRequestBody("Transaction.Paid", PAYMENT_ID, TRANSACTION_ID);
            doThrow(new WebhookVerificationException("Invalid signature", null))
                    .when(webhookVerifier).verify(anyString(), anyString(), anyString(), anyString());

            // when & then
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, "invalid-signature")
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(webhookService, never()).handleWebhook(any());
        }

        @Test
        @DisplayName("서비스 예외 발생 - 200 OK 반환 (재시도 방지)")
        void handlePortOneWebhook_serviceException_returns200() throws Exception {
            // given
            String requestBody = createWebhookRequestBody("Transaction.Paid", PAYMENT_ID, TRANSACTION_ID);
            doThrow(new RuntimeException("Service error"))
                    .when(webhookService).handleWebhook(any(WebhookRequest.class));

            // when & then
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, WEBHOOK_SIGNATURE)
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(webhookService, times(1)).handleWebhook(any(WebhookRequest.class));
        }

        @Test
        @DisplayName("JSON 파싱 실패 - 200 OK 반환 (재시도 방지)")
        void handlePortOneWebhook_jsonParsingFailed_returns200() throws Exception {
            // given
            String invalidJson = "{\"invalid\": json}";

            // when & then
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, WEBHOOK_SIGNATURE)
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isOk());

            verify(webhookService, never()).handleWebhook(any());
        }

        @Test
        @DisplayName("헤더 누락 - 검증 실패하지만 200 OK 반환")
        void handlePortOneWebhook_missingHeaders_returns200() throws Exception {
            // given
            String requestBody = createWebhookRequestBody("Transaction.Paid", PAYMENT_ID, TRANSACTION_ID);
            doThrow(new WebhookVerificationException("Missing headers", null))
                    .when(webhookVerifier).verify(any(), any(), any(), any());

            // when & then
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(webhookService, never()).handleWebhook(any());
        }

        @Test
        @DisplayName("검증 - 웹훅 처리 전 서명 검증 수행")
        void handlePortOneWebhook_verifyBeforeProcessing() throws Exception {
            // given
            String requestBody = createWebhookRequestBody("Transaction.Paid", PAYMENT_ID, TRANSACTION_ID);

            // when
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, WEBHOOK_SIGNATURE)
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            // then - verify 호출 후 handleWebhook 호출
            var inOrder = inOrder(webhookVerifier, webhookService);
            inOrder.verify(webhookVerifier).verify(requestBody, WEBHOOK_ID, WEBHOOK_SIGNATURE, WEBHOOK_TIMESTAMP);
            inOrder.verify(webhookService).handleWebhook(any(WebhookRequest.class));
        }

        @Test
        @DisplayName("검증 - 올바른 WebhookRequest 객체 전달")
        void handlePortOneWebhook_passesCorrectRequest() throws Exception {
            // given
            String requestBody = createWebhookRequestBody("Transaction.Paid", PAYMENT_ID, TRANSACTION_ID);

            // when
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, WEBHOOK_SIGNATURE)
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            // then - WebhookRequest의 타입과 데이터가 올바르게 파싱되었는지 검증
            ArgumentCaptor<WebhookRequest> captor = ArgumentCaptor.forClass(WebhookRequest.class);
            verify(webhookService, times(1)).handleWebhook(captor.capture());

            WebhookRequest captured = captor.getValue();
            assertThat(captured.type()).isEqualTo("Transaction.Paid");
            assertThat(captured.data().paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(captured.data().transactionId()).isEqualTo(TRANSACTION_ID);
        }

        @Test
        @DisplayName("멱등성 - 동일 웹훅 재수신 시에도 200 OK")
        void handlePortOneWebhook_idempotent() throws Exception {
            // given
            String requestBody = createWebhookRequestBody("Transaction.Paid", PAYMENT_ID, TRANSACTION_ID);

            // when - 동일 요청 2번 전송
            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, WEBHOOK_SIGNATURE)
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            mockMvc.perform(post(WEBHOOK_ENDPOINT)
                            .header(WebhookVerifier.HEADER_ID, WEBHOOK_ID)
                            .header(WebhookVerifier.HEADER_SIGNATURE, WEBHOOK_SIGNATURE)
                            .header(WebhookVerifier.HEADER_TIMESTAMP, WEBHOOK_TIMESTAMP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            // then - 서비스는 2번 호출됨 (멱등성 처리는 서비스 계층에서)
            verify(webhookService, times(2)).handleWebhook(any(WebhookRequest.class));
        }
    }
}
