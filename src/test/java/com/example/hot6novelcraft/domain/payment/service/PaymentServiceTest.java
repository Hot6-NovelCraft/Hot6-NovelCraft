package com.example.hot6novelcraft.domain.payment.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.notification.entity.enums.NotificationType;
import com.example.hot6novelcraft.domain.notification.producer.NotificationProducer;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentHistoryResponse;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentPrepareResponse;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentResponse;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentMethod;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService 테스트")
class PaymentServiceTest {

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
    private static final Long PAYMENT_ID = 100L;
    private static final Long AMOUNT = 10000L;
    private static final String PAYMENT_KEY = "test-payment-key-123";

    private Payment createMockPayment(Long id, Long userId, Long amount, String paymentKey, PaymentStatus status) {
        Payment payment = mock(Payment.class);
        given(payment.getId()).willReturn(id);
        given(payment.getUserId()).willReturn(userId);
        given(payment.getAmount()).willReturn(amount);
        given(payment.getPaymentKey()).willReturn(paymentKey);
        given(payment.getStatus()).willReturn(status);
        given(payment.getMethod()).willReturn(PaymentMethod.CARD);
        return payment;
    }

    // =========================================================
    // 결제 내역 조회 테스트
    // =========================================================
    @Nested
    @DisplayName("getPaymentHistory() - 결제 내역 조회")
    class GetPaymentHistoryTest {

        @Test
        @DisplayName("성공 - 결제 내역 조회")
        void getPaymentHistory_success() {
            // given
            Payment payment1 = createMockPayment(1L, USER_ID, 10000L, "key1", PaymentStatus.COMPLETED);
            Payment payment2 = createMockPayment(2L, USER_ID, 20000L, "key2", PaymentStatus.COMPLETED);
            given(payment1.getCreatedAt()).willReturn(LocalDateTime.now());
            given(payment2.getCreatedAt()).willReturn(LocalDateTime.now());

            Page<Payment> paymentsPage = new PageImpl<>(List.of(payment1, payment2), PageRequest.of(0, 10), 2);
            given(paymentRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                    .willReturn(paymentsPage);

            // when
            PageResponse<PaymentHistoryResponse> result = paymentService.getPaymentHistory(USER_ID, PageRequest.of(0, 10));

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(2);
            assertThat(result.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("성공 - 결제 내역 없음")
        void getPaymentHistory_empty() {
            // given
            Page<Payment> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            given(paymentRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                    .willReturn(emptyPage);

            // when
            PageResponse<PaymentHistoryResponse> result = paymentService.getPaymentHistory(USER_ID, PageRequest.of(0, 10));

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
        }
    }

    // =========================================================
    // 결제 준비 테스트
    // =========================================================
    @Nested
    @DisplayName("preparePayment() - 결제 준비")
    class PreparePaymentTest {

        @Test
        @DisplayName("성공 - 결제 준비")
        void preparePayment_success() {
            // given
            PaymentPrepareResponse mockResponse = new PaymentPrepareResponse(PAYMENT_KEY, AMOUNT);
            given(paymentTransactionService.preparePendingPayment(USER_ID, AMOUNT))
                    .willReturn(mockResponse);

            // when
            PaymentPrepareResponse result = paymentService.preparePayment(USER_ID, AMOUNT);

            // then
            assertThat(result).isNotNull();
            assertThat(result.paymentKey()).isEqualTo(PAYMENT_KEY);
            assertThat(result.amount()).isEqualTo(AMOUNT);

            verify(paymentTransactionService, times(1)).preparePendingPayment(USER_ID, AMOUNT);
        }
    }

    // =========================================================
    // 결제 확인 테스트
    // =========================================================
    @Nested
    @DisplayName("confirmPayment() - 결제 확인")
    class ConfirmPaymentTest {

        @Test
        @DisplayName("성공 - 결제 확인 및 포인트 충전")
        void confirmPayment_success() throws Exception {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment pendingPayment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT, PAYMENT_KEY, PaymentStatus.PENDING);
            given(paymentTransactionService.savePendingPayment(USER_ID, request))
                    .willReturn(pendingPayment);

            // PortOne Mock
            PaidPayment paidPayment = mock(PaidPayment.class);
            PaymentAmount paymentAmount = mock(PaymentAmount.class);
            given(paymentAmount.getTotal()).willReturn(AMOUNT);
            given(paidPayment.getAmount()).willReturn(paymentAmount);
            given(paidPayment.getMethod()).willReturn(null);
            given(paymentClient.getPayment(PAYMENT_KEY))
                    .willReturn(CompletableFuture.completedFuture(paidPayment));

            Payment completedPayment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT, PAYMENT_KEY, PaymentStatus.COMPLETED);
            given(paymentTransactionService.completePayment(eq(PAYMENT_ID), eq(USER_ID), eq(AMOUNT), any()))
                    .willReturn(completedPayment);
            given(pointService.getBalance(USER_ID)).willReturn(20000L);

            // when
            PaymentResponse result = paymentService.confirmPayment(USER_ID, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED.name());

            verify(redisUtil, times(1)).acquireLock(eq("payment:confirm:lock:" + PAYMENT_KEY));
            verify(redisUtil, times(1)).releaseLock(eq("payment:confirm:lock:" + PAYMENT_KEY));
            verify(paymentTransactionService, times(1)).completePayment(eq(PAYMENT_ID), eq(USER_ID), eq(AMOUNT), any());
            verify(notificationProducer, times(1)).publish(argThat(e ->
                    e.userId().equals(USER_ID) && e.type() == NotificationType.POINT_CHARGE));
        }

        @Test
        @DisplayName("실패 - 락 획득 실패")
        void confirmPayment_lockNotAcquired() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPayment(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_PROCESSING.getMessage());

            verify(paymentTransactionService, never()).savePendingPayment(any(), any());
            verify(redisUtil, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("실패 - 금액 불일치 (위변조)")
        void confirmPayment_amountMismatch() throws Exception {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment pendingPayment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT, PAYMENT_KEY, PaymentStatus.PENDING);
            given(paymentTransactionService.savePendingPayment(USER_ID, request))
                    .willReturn(pendingPayment);

            // PortOne Mock - 금액 불일치
            PaidPayment paidPayment = mock(PaidPayment.class);
            PaymentAmount paymentAmount = mock(PaymentAmount.class);
            given(paymentAmount.getTotal()).willReturn(AMOUNT + 1000L);  // 다른 금액
            given(paidPayment.getAmount()).willReturn(paymentAmount);
            given(paymentClient.getPayment(PAYMENT_KEY))
                    .willReturn(CompletableFuture.completedFuture(paidPayment));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPayment(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_AMOUNT_MISMATCH.getMessage());

            verify(paymentTransactionService, times(1)).failPayment(PAYMENT_ID);
            verify(notificationProducer, times(1)).publish(argThat(e ->
                    e.userId().equals(USER_ID) && e.type() == NotificationType.PAYMENT_FAILED));
            verify(redisUtil, times(1)).releaseLock(eq("payment:confirm:lock:" + PAYMENT_KEY));
        }

        @Test
        @DisplayName("실패 - PortOne API 오류")
        void confirmPayment_portOneError() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment pendingPayment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT, PAYMENT_KEY, PaymentStatus.PENDING);
            given(paymentTransactionService.savePendingPayment(USER_ID, request))
                    .willReturn(pendingPayment);

            given(paymentClient.getPayment(PAYMENT_KEY))
                    .willReturn(CompletableFuture.failedFuture(new RuntimeException("PortOne 연결 오류")));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPayment(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PORTONE_API_ERROR.getMessage());

            verify(paymentTransactionService, times(1)).failPayment(PAYMENT_ID);
            verify(redisUtil, times(1)).releaseLock(eq("payment:confirm:lock:" + PAYMENT_KEY));
        }

        @Test
        @DisplayName("검증 - 예외 발생 시 finally에서 락 해제")
        void confirmPayment_lockReleasedOnException() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            given(redisUtil.acquireLock(anyString())).willReturn(true);
            given(paymentTransactionService.savePendingPayment(USER_ID, request))
                    .willThrow(new RuntimeException("Unexpected error"));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPayment(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class);

            verify(redisUtil, times(1)).releaseLock(eq("payment:confirm:lock:" + PAYMENT_KEY));
        }
    }

    // =========================================================
    // 환불 테스트
    // =========================================================
    @Nested
    @DisplayName("cancelPayment() - 환불")
    class CancelPaymentTest {

        @Test
        @DisplayName("성공 - 환불 처리")
        void cancelPayment_success() throws Exception {
            // given
            String reason = "단순 변심";
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment completedPayment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT, PAYMENT_KEY, PaymentStatus.COMPLETED);
            given(paymentTransactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .willReturn(completedPayment);

            given(paymentClient.cancelPayment(eq(PAYMENT_KEY), isNull(), isNull(), isNull(),
                    eq(reason), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .willReturn(CompletableFuture.completedFuture(null));

            Payment cancelledPayment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT, PAYMENT_KEY, PaymentStatus.REFUNDED);
            given(paymentTransactionService.finalizeCancel(PAYMENT_ID))
                    .willReturn(cancelledPayment);

            // when
            PaymentResponse result = paymentService.cancelPayment(USER_ID, PAYMENT_ID, reason);

            // then
            assertThat(result).isNotNull();
            assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(result.status()).isEqualTo(PaymentStatus.REFUNDED.name());

            verify(pointService, times(1)).deduct(USER_ID, AMOUNT);
            verify(paymentTransactionService, times(1)).finalizeCancel(PAYMENT_ID);
            verify(notificationProducer, times(1)).publish(argThat(e ->
                    e.userId().equals(USER_ID) && e.type() == NotificationType.PAYMENT_REFUNDED));
            verify(redisUtil, times(1)).acquireLock(eq("payment:cancel:lock:" + PAYMENT_ID));
            verify(redisUtil, times(1)).releaseLock(eq("payment:cancel:lock:" + PAYMENT_ID));
            verify(pointService, never()).compensateDeduct(any(), any());
        }

        @Test
        @DisplayName("실패 - 락 획득 실패")
        void cancelPayment_lockNotAcquired() {
            // given
            given(redisUtil.acquireLock(anyString())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> paymentService.cancelPayment(USER_ID, PAYMENT_ID, "단순 변심"))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_CANCELING.getMessage());

            verify(paymentTransactionService, never()).getPaymentForCancel(any(), any());
            verify(pointService, never()).deduct(any(), any());
        }

        @Test
        @DisplayName("실패 - PortOne 환불 실패 시 포인트 복구")
        void cancelPayment_portOneFailure_pointCompensated() {
            // given
            String reason = "단순 변심";
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment completedPayment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT, PAYMENT_KEY, PaymentStatus.COMPLETED);
            given(paymentTransactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .willReturn(completedPayment);

            given(paymentClient.cancelPayment(eq(PAYMENT_KEY), isNull(), isNull(), isNull(),
                    eq(reason), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .willReturn(CompletableFuture.failedFuture(new RuntimeException("PortOne 환불 실패")));

            // when & then
            assertThatThrownBy(() -> paymentService.cancelPayment(USER_ID, PAYMENT_ID, reason))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PORTONE_API_ERROR.getMessage());

            verify(pointService, times(1)).deduct(USER_ID, AMOUNT);
            verify(pointService, times(1)).compensateDeduct(USER_ID, AMOUNT);  // 보상 트랜잭션
            verify(paymentTransactionService, never()).finalizeCancel(any());
            verify(redisUtil, times(1)).releaseLock(eq("payment:cancel:lock:" + PAYMENT_ID));
        }

        @Test
        @DisplayName("검증 - 포인트 선차감 후 PortOne 호출")
        void cancelPayment_pointDeductedBeforePortOne() throws Exception {
            // given
            String reason = "단순 변심";
            given(redisUtil.acquireLock(anyString())).willReturn(true);

            Payment completedPayment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT, PAYMENT_KEY, PaymentStatus.COMPLETED);
            given(paymentTransactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .willReturn(completedPayment);

            given(paymentClient.cancelPayment(eq(PAYMENT_KEY), isNull(), isNull(), isNull(),
                    eq(reason), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .willReturn(CompletableFuture.completedFuture(null));

            Payment cancelledPayment = createMockPayment(PAYMENT_ID, USER_ID, AMOUNT, PAYMENT_KEY, PaymentStatus.REFUNDED);
            given(paymentTransactionService.finalizeCancel(PAYMENT_ID))
                    .willReturn(cancelledPayment);

            // when
            paymentService.cancelPayment(USER_ID, PAYMENT_ID, reason);

            // then
            // 포인트 차감이 PortOne 호출보다 먼저 실행되었는지 검증
            var inOrder = inOrder(pointService, paymentClient);
            inOrder.verify(pointService).deduct(USER_ID, AMOUNT);
            inOrder.verify(paymentClient).cancelPayment(eq(PAYMENT_KEY), any(), any(), any(),
                    eq(reason), any(), any(), any(), any(), any(), any());
        }
    }
}
