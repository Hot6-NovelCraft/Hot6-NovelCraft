package com.example.hot6novelcraft.domain.payment.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.notification.producer.NotificationProducer;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.payment.repository.PaymentRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PaymentAmount;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("결제 동시성 테스트")
class PaymentConcurrencyTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PaymentTransactionService paymentTransactionService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private PointService pointService;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private NotificationProducer notificationProducer;

    private static final Long USER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;
    private static final Long AMOUNT = 10_000L;
    private static final String PAYMENT_KEY = "payment-test-abc123";
    private static final String CANCEL_REASON = "테스트 환불";

    private Payment completedPaymentMock() {
        Payment payment = mock(Payment.class);
        given(payment.getId()).willReturn(PAYMENT_ID);
        given(payment.getUserId()).willReturn(USER_ID);
        given(payment.getAmount()).willReturn(AMOUNT);
        given(payment.getPaymentKey()).willReturn(PAYMENT_KEY);
        given(payment.getStatus()).willReturn(PaymentStatus.COMPLETED);
        return payment;
    }

    // =========================================================
    // 환불(cancelPayment) 동시성 테스트
    // =========================================================
    @Nested
    @DisplayName("환불 동시성 테스트")
    class CancelPaymentConcurrencyTest {

        @Test
        @DisplayName("락 획득 실패 시 ERR_PAYMENT_ALREADY_CANCELING 예외 반환")
        void cancelPayment_whenLockNotAcquired_throwsAlreadyCanceling() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> paymentService.cancelPayment(USER_ID, PAYMENT_ID, CANCEL_REASON))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_CANCELING.getMessage());

            verify(paymentTransactionService, never()).getPaymentForCancel(any(), any());
            verify(pointService, never()).deduct(any(), any());
        }

        @Test
        @DisplayName("동시 환불 요청 2개 - 락으로 포인트 차감 1번만 실행")
        void cancelPayment_concurrentTwoRequests_deductCalledOnlyOnce() throws InterruptedException {
            // given
            AtomicBoolean lockHeld = new AtomicBoolean(false);
            given(redisUtil.acquireLock(anyString()))
                    .willAnswer(inv -> lockHeld.compareAndSet(false, true));

            Payment completedPayment = completedPaymentMock();
            given(paymentTransactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .willReturn(completedPayment);

            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger alreadyCancelingCount = new AtomicInteger(0);
            AtomicInteger otherErrorCount = new AtomicInteger(0);
            List<Exception> unexpectedErrors = new ArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        paymentService.cancelPayment(USER_ID, PAYMENT_ID, CANCEL_REASON);
                    } catch (ServiceErrorException e) {
                        if (e.getMessage().equals(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_CANCELING.getMessage())) {
                            alreadyCancelingCount.incrementAndGet();
                        } else {
                            otherErrorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpectedErrors.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(completed).isTrue();
            assertThat(unexpectedErrors).isEmpty();
            assertThat(alreadyCancelingCount.get()).isEqualTo(1);
            assertThat(alreadyCancelingCount.get() + otherErrorCount.get()).isEqualTo(2);
            verify(pointService, times(1)).deduct(USER_ID, AMOUNT);
        }

        @Test
        @DisplayName("10개 동시 요청 중 락을 획득한 1개만 포인트 차감 실행")
        void cancelPayment_tenConcurrentRequests_deductCalledOnlyOnce() throws InterruptedException {
            // given
            AtomicBoolean lockHeld = new AtomicBoolean(false);
            given(redisUtil.acquireLock(anyString()))
                    .willAnswer(inv -> lockHeld.compareAndSet(false, true));

            Payment completedPayment = completedPaymentMock();
            given(paymentTransactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .willReturn(completedPayment);

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger alreadyCancelingCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        paymentService.cancelPayment(USER_ID, PAYMENT_ID, CANCEL_REASON);
                    } catch (ServiceErrorException e) {
                        if (e.getMessage().equals(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_CANCELING.getMessage())) {
                            alreadyCancelingCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(alreadyCancelingCount.get()).isEqualTo(9);
            verify(pointService, times(1)).deduct(USER_ID, AMOUNT);
        }

        @Test
        @DisplayName("포트원 실패 시 포인트 복구 후 락 해제")
        void cancelPayment_whenPortOneFails_compensatesAndReleasesLock() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment completedPayment = completedPaymentMock();
            given(paymentTransactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .willReturn(completedPayment);

            // when & then
            assertThatThrownBy(() -> paymentService.cancelPayment(USER_ID, PAYMENT_ID, CANCEL_REASON))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PORTONE_API_ERROR.getMessage());

            verify(pointService, times(1)).deduct(USER_ID, AMOUNT);
            verify(pointService, times(1)).compensateDeduct(USER_ID, AMOUNT);
            verify(redisUtil, times(1)).releaseLock(eq("payment:cancel:lock:" + PAYMENT_ID));
        }

        @Test
        @DisplayName("PG사 환불 API 20초 타임아웃 시 포인트 복구 후 락 해제")
        void cancelPayment_whenPortOneTimesOut_compensatesAndReleasesLock() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment completedPayment = completedPaymentMock();
            given(paymentTransactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .willReturn(completedPayment);
            given(paymentClient.cancelPayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(java.util.concurrent.CompletableFuture
                            .failedFuture(new TimeoutException("PG API 20초 타임아웃")));

            // when & then
            assertThatThrownBy(() -> paymentService.cancelPayment(USER_ID, PAYMENT_ID, CANCEL_REASON))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PORTONE_API_ERROR.getMessage());

            verify(pointService, times(1)).deduct(USER_ID, AMOUNT);
            verify(pointService, times(1)).compensateDeduct(USER_ID, AMOUNT);
            verify(redisUtil, times(1)).releaseLock(eq("payment:cancel:lock:" + PAYMENT_ID));
        }
    }

    // =========================================================
    // 결제 확인(confirmPayment) 동시성 테스트
    // =========================================================
    @Nested
    @DisplayName("결제 확인 동시성 테스트")
    class ConfirmPaymentConcurrencyTest {

        private PaymentConfirmRequest confirmRequest() {
            return new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
        }

        @Test
        @DisplayName("락 획득 실패 시 ERR_PAYMENT_PROCESSING 예외 반환")
        void confirmPayment_whenLockNotAcquired_throwsPaymentProcessing() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPayment(USER_ID, confirmRequest()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage());

            verify(paymentTransactionService, never()).savePendingPayment(any(), any());
            verify(pointService, never()).charge(any(), any());
        }

        @Test
        @DisplayName("동시 결제 확인 2개 - 락으로 포인트 충전 1번만 실행")
        void confirmPayment_concurrentTwoRequests_chargeCalledOnlyOnce() throws InterruptedException {
            // given
            AtomicBoolean lockHeld = new AtomicBoolean(false);
            given(redisUtil.acquireLock(anyString()))
                    .willAnswer(inv -> lockHeld.compareAndSet(false, true));

            Payment pendingPayment = mock(Payment.class);
            given(pendingPayment.getId()).willReturn(PAYMENT_ID);
            given(paymentTransactionService.savePendingPayment(anyLong(), any()))
                    .willReturn(pendingPayment);

            PaidPayment paidPayment = mock(PaidPayment.class);
            PaymentAmount paymentAmount = mock(PaymentAmount.class);
            given(paymentAmount.getTotal()).willReturn(AMOUNT);
            given(paidPayment.getAmount()).willReturn(paymentAmount);
            given(paidPayment.getMethod()).willReturn(null);

            Payment completedPayment = mock(Payment.class);
            given(completedPayment.getId()).willReturn(PAYMENT_ID);
            given(completedPayment.getStatus()).willReturn(PaymentStatus.COMPLETED);
            given(paymentTransactionService.completePayment(anyLong(), anyLong(), anyLong(), any()))
                    .willReturn(completedPayment);
            given(paymentClient.getPayment(anyString()))
                    .willReturn(java.util.concurrent.CompletableFuture.completedFuture(paidPayment));

            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger processingCount = new AtomicInteger(0);
            List<Exception> unexpectedErrors = new ArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        paymentService.confirmPayment(USER_ID, confirmRequest());
                        successCount.incrementAndGet();
                    } catch (ServiceErrorException e) {
                        if (e.getMessage().equals(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage())) {
                            processingCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(e);
                        }
                    } catch (Exception e) {
                        unexpectedErrors.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(unexpectedErrors).isEmpty();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(processingCount.get()).isEqualTo(1);
            verify(paymentTransactionService, times(1))
                    .completePayment(anyLong(), anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("포트원 검증 중 예외 발생 시 finally에서 락 반드시 해제")
        void confirmPayment_whenPortOneFails_lockMustBeReleased() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment pendingPayment = mock(Payment.class);
            given(pendingPayment.getId()).willReturn(PAYMENT_ID);
            given(paymentTransactionService.savePendingPayment(anyLong(), any()))
                    .willReturn(pendingPayment);
            given(paymentClient.getPayment(anyString()))
                    .willReturn(java.util.concurrent.CompletableFuture
                            .failedFuture(new RuntimeException("PortOne 연결 오류")));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPayment(USER_ID, confirmRequest()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PORTONE_API_ERROR.getMessage());

            verify(redisUtil, times(1)).releaseLock(eq("payment:confirm:lock:" + PAYMENT_KEY));
        }

        @Test
        @DisplayName("PG사 조회 API 10초 타임아웃 시 결제 실패 처리 후 락 해제")
        void confirmPayment_whenPortOneTimesOut_failsPaymentAndReleasesLock() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment pendingPayment = mock(Payment.class);
            given(pendingPayment.getId()).willReturn(PAYMENT_ID);
            given(paymentTransactionService.savePendingPayment(anyLong(), any()))
                    .willReturn(pendingPayment);
            given(paymentClient.getPayment(anyString()))
                    .willReturn(java.util.concurrent.CompletableFuture
                            .failedFuture(new TimeoutException("PG API 10초 타임아웃")));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPayment(USER_ID, confirmRequest()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PORTONE_API_ERROR.getMessage());

            verify(paymentTransactionService, times(1)).failPayment(PAYMENT_ID);
            verify(redisUtil, times(1)).releaseLock(eq("payment:confirm:lock:" + PAYMENT_KEY));
        }
    }
}
