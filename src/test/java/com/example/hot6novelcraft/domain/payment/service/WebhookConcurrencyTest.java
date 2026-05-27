package com.example.hot6novelcraft.domain.payment.service;

import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.payment.dto.request.WebhookRequest;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PaymentClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("웹훅 동시성 테스트")
class WebhookConcurrencyTest {

    @InjectMocks
    private WebhookService webhookService;

    @Mock
    private WebhookTransactionService webhookTransactionService;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private RedisUtil redisUtil;

    private static final Long PAYMENT_DB_ID = 10L;
    private static final Long WEBHOOK_EVENT_ID = 99L;
    private static final String PORTONE_PAYMENT_ID = "payment-test-abc123";
    private static final String TRANSACTION_ID = "tx-test-001";

    private WebhookRequest paidWebhookRequest() {
        WebhookRequest.WebhookData data = new WebhookRequest.WebhookData(
                PORTONE_PAYMENT_ID, TRANSACTION_ID, null
        );
        return new WebhookRequest("Transaction.Paid", data);
    }

    private Payment pendingPayment() {
        Payment payment = mock(Payment.class);
        given(payment.getId()).willReturn(PAYMENT_DB_ID);
        given(payment.getStatus()).willReturn(PaymentStatus.PENDING);
        return payment;
    }

    private WebhookEvent webhookEvent(Long id) {
        WebhookEvent event = mock(WebhookEvent.class);
        given(event.getId()).willReturn(id);
        return event;
    }

    // =========================================================
    // 웹훅 락 동시성 테스트
    // =========================================================
    @Nested
    @DisplayName("웹훅 락 동시성 테스트")
    class WebhookLockConcurrencyTest {

        @Test
        @DisplayName("/confirm 처리 중 웹훅 도착 시 WebhookEvent를 PENDING 유지하여 포트원 재시도 유도")
        void webhook_whenConfirmHoldsLock_returnWithoutMarkingFailed() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            WebhookEvent event = webhookEvent(WEBHOOK_EVENT_ID);
            given(webhookTransactionService.prepareWebhookEvent(anyString(), any(), anyString(), any()))
                    .willReturn(event);

            PaidPayment paidPayment = mock(PaidPayment.class);
            given(paidPayment.getMethod()).willReturn(null);
            given(paymentClient.getPayment(anyString()))
                    .willReturn(CompletableFuture.completedFuture(paidPayment));

            Payment payment = pendingPayment();
            given(webhookTransactionService.getPaymentByKey(anyString())).willReturn(payment);

            // when
            webhookService.handleWebhook(paidWebhookRequest());

            // then
            verify(webhookTransactionService, never()).completePendingPayment(any(), any(), any());
            verify(webhookTransactionService, never()).markEventFailed(any(), anyString());
        }

        @Test
        @DisplayName("웹훅 락 획득 성공 시 completePendingPayment 정상 실행")
        void webhook_whenLockAcquired_completesPayment() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);
            willDoNothing().given(redisUtil).releaseLock(anyString());

            WebhookEvent event = webhookEvent(WEBHOOK_EVENT_ID);
            given(webhookTransactionService.prepareWebhookEvent(anyString(), any(), anyString(), any()))
                    .willReturn(event);

            PaidPayment paidPayment = mock(PaidPayment.class);
            given(paidPayment.getMethod()).willReturn(null);
            given(paymentClient.getPayment(anyString()))
                    .willReturn(CompletableFuture.completedFuture(paidPayment));

            Payment payment = pendingPayment();
            given(webhookTransactionService.getPaymentByKey(anyString())).willReturn(payment);
            willDoNothing().given(webhookTransactionService).completePendingPayment(anyLong(), anyLong(), any());

            // when
            webhookService.handleWebhook(paidWebhookRequest());

            // then
            verify(webhookTransactionService, times(1)).completePendingPayment(anyLong(), anyLong(), any());
            verify(webhookTransactionService, never()).markEventFailed(any(), anyString());
            verify(redisUtil, times(1)).releaseLock(eq("payment:confirm:lock:" + PORTONE_PAYMENT_ID));
        }

        @Test
        @DisplayName("동시 웹훅 2개 - 락 경합으로 1개만 completePendingPayment 실행")
        void webhook_twoSimultaneousWebhooks_onlyOneCompletesPayment() throws InterruptedException {
            // given
            WebhookEvent event1 = webhookEvent(WEBHOOK_EVENT_ID);
            WebhookEvent event2 = webhookEvent(WEBHOOK_EVENT_ID + 1);
            given(webhookTransactionService.prepareWebhookEvent(anyString(), any(), anyString(), any()))
                    .willReturn(event1).willReturn(event2);

            PaidPayment paidPayment = mock(PaidPayment.class);
            given(paidPayment.getMethod()).willReturn(null);
            given(paymentClient.getPayment(anyString()))
                    .willReturn(CompletableFuture.completedFuture(paidPayment));

            Payment payment = pendingPayment();
            given(webhookTransactionService.getPaymentByKey(anyString())).willReturn(payment);

            AtomicBoolean lockHeld = new AtomicBoolean(false);
            given(redisUtil.acquireLock(anyString()))
                    .willAnswer(inv -> lockHeld.compareAndSet(false, true));
            willDoNothing().given(redisUtil).releaseLock(anyString());
            willDoNothing().given(webhookTransactionService).completePendingPayment(anyLong(), anyLong(), any());

            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        webhookService.handleWebhook(paidWebhookRequest());
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(errorCount.get()).isZero();
            verify(webhookTransactionService, times(1)).completePendingPayment(anyLong(), anyLong(), any());
            verify(webhookTransactionService, never()).markEventFailed(any(), anyString());
        }

        @Test
        @DisplayName("이미 최종 상태인 Payment는 락 획득 시도 없이 스킵")
        void webhook_whenPaymentAlreadyTerminal_skipsWithoutAcquiringLock() {
            // given
            WebhookEvent event = webhookEvent(WEBHOOK_EVENT_ID);
            given(webhookTransactionService.prepareWebhookEvent(anyString(), any(), anyString(), any()))
                    .willReturn(event);

            PaidPayment paidPayment = mock(PaidPayment.class);
            given(paymentClient.getPayment(anyString()))
                    .willReturn(CompletableFuture.completedFuture(paidPayment));

            Payment completedPayment = mock(Payment.class);
            given(completedPayment.getStatus()).willReturn(PaymentStatus.COMPLETED);
            given(webhookTransactionService.getPaymentByKey(anyString())).willReturn(completedPayment);
            willDoNothing().given(webhookTransactionService).markEventComplete(anyLong());

            // when
            webhookService.handleWebhook(paidWebhookRequest());

            // then
            verify(redisUtil, never()).acquireLock(anyString());
            verify(webhookTransactionService, never()).completePendingPayment(any(), any(), any());
            verify(webhookTransactionService, times(1)).markEventComplete(WEBHOOK_EVENT_ID);
        }
    }

    // =========================================================
    // 락 해제 보장 테스트
    // =========================================================
    @Nested
    @DisplayName("예외 발생 시 락 해제 보장")
    class WebhookLockReleaseTest {

        @Test
        @DisplayName("completePendingPayment 중 예외 발생해도 finally에서 락 반드시 해제")
        void webhook_whenCompletePendingPaymentThrows_lockMustBeReleased() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);
            willDoNothing().given(redisUtil).releaseLock(anyString());

            WebhookEvent event = webhookEvent(WEBHOOK_EVENT_ID);
            given(webhookTransactionService.prepareWebhookEvent(anyString(), any(), anyString(), any()))
                    .willReturn(event);

            PaidPayment paidPayment = mock(PaidPayment.class);
            given(paidPayment.getMethod()).willReturn(null);
            given(paymentClient.getPayment(anyString()))
                    .willReturn(CompletableFuture.completedFuture(paidPayment));

            Payment payment = pendingPayment();
            given(webhookTransactionService.getPaymentByKey(anyString())).willReturn(payment);
            willThrow(new RuntimeException("DB 오류"))
                    .given(webhookTransactionService).completePendingPayment(anyLong(), anyLong(), any());

            // when
            try {
                webhookService.handleWebhook(paidWebhookRequest());
            } catch (Exception ignored) {
            }

            // then
            verify(redisUtil, times(1)).releaseLock(eq("payment:confirm:lock:" + PORTONE_PAYMENT_ID));
        }
    }
}
