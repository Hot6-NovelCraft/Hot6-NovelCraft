package com.example.hot6novelcraft.domain.payment.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentPrepareResponse;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentMethod;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.payment.repository.PaymentRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.example.hot6novelcraft.domain.purchases.entity.Purchase;
import com.example.hot6novelcraft.domain.purchases.entity.enums.PurchaseType;
import com.example.hot6novelcraft.domain.purchases.repository.PurchaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentTransactionService 테스트")
class PaymentTransactionServiceTest {

    @InjectMocks
    private PaymentTransactionService transactionService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PointService pointService;

    private static final Long USER_ID = 1L;
    private static final Long AMOUNT = 10000L;
    private static final Long PAYMENT_ID = 100L;
    private static final String PAYMENT_KEY = "payment-test-key-12345";

    private Payment createMockPayment(Long id, Long userId, String paymentKey, Long amount, PaymentStatus status) {
        Payment payment = mock(Payment.class);
        given(payment.getId()).willReturn(id);
        given(payment.getUserId()).willReturn(userId);
        given(payment.getPaymentKey()).willReturn(paymentKey);
        given(payment.getAmount()).willReturn(amount);
        given(payment.getStatus()).willReturn(status);
        return payment;
    }

    // =========================================================
    // preparePendingPayment() - PENDING Payment 생성
    // =========================================================
    @Nested
    @DisplayName("preparePendingPayment() - PENDING Payment 생성")
    class PreparePendingPaymentTest {

        @Test
        @DisplayName("성공 - PENDING Payment 생성 및 paymentKey 반환")
        void preparePendingPayment_success() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.save(any(Payment.class))).willReturn(mockPayment);

            // when
            PaymentPrepareResponse response = transactionService.preparePendingPayment(USER_ID, AMOUNT);

            // then
            assertThat(response).isNotNull();
            assertThat(response.paymentKey()).isEqualTo(PAYMENT_KEY);
            assertThat(response.amount()).isEqualTo(AMOUNT);

            verify(paymentRepository, times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("검증 - paymentKey는 'payment-' prefix를 가짐")
        void preparePendingPayment_paymentKeyFormat() {
            // given
            Payment mockPayment = mock(Payment.class);
            given(mockPayment.getPaymentKey()).willReturn(PAYMENT_KEY);
            given(mockPayment.getAmount()).willReturn(AMOUNT);
            given(paymentRepository.save(any(Payment.class))).willReturn(mockPayment);

            // when
            PaymentPrepareResponse response = transactionService.preparePendingPayment(USER_ID, AMOUNT);

            // then
            assertThat(response.paymentKey()).startsWith("payment-");
        }
    }

    // =========================================================
    // savePendingPayment() - PENDING Payment 검증 및 조회
    // =========================================================
    @Nested
    @DisplayName("savePendingPayment() - PENDING Payment 검증 및 조회")
    class SavePendingPaymentTest {

        @Test
        @DisplayName("성공 - PENDING Payment 반환")
        void savePendingPayment_success() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(mockPayment));

            // when
            Payment result = transactionService.savePendingPayment(USER_ID, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(PAYMENT_ID);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("실패 - /prepare 없이 /confirm 호출 시 ERR_PAYMENT_NOT_FOUND")
        void savePendingPayment_notPrepared_throwsException() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest("invalid-key", AMOUNT);
            given(paymentRepository.findByPaymentKey("invalid-key")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.savePendingPayment(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 소유자 불일치 시 ERR_NOT_MY_ORDER")
        void savePendingPayment_ownerMismatch_throwsException() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            Payment mockPayment = createMockPayment(PAYMENT_ID, 999L, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(mockPayment));

            // when & then
            assertThatThrownBy(() -> transactionService.savePendingPayment(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_NOT_MY_ORDER.getMessage());
        }

        @Test
        @DisplayName("실패 - 금액 불일치 시 ERR_AMOUNT_MISMATCH")
        void savePendingPayment_amountMismatch_throwsException() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, 99999L);
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(mockPayment));

            // when & then
            assertThatThrownBy(() -> transactionService.savePendingPayment(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_AMOUNT_MISMATCH.getMessage());
        }

        @Test
        @DisplayName("실패 - 이미 처리된 결제(COMPLETED) 시 ERR_ALREADY_PAID")
        void savePendingPayment_alreadyCompleted_throwsException() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.COMPLETED);
            given(paymentRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(mockPayment));

            // when & then
            assertThatThrownBy(() -> transactionService.savePendingPayment(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_ALREADY_PAID.getMessage());
        }

        @Test
        @DisplayName("실패 - 이미 처리된 결제(REFUNDED) 시 ERR_ALREADY_PAID")
        void savePendingPayment_alreadyRefunded_throwsException() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(PAYMENT_KEY, AMOUNT);
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.REFUNDED);
            given(paymentRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(mockPayment));

            // when & then
            assertThatThrownBy(() -> transactionService.savePendingPayment(USER_ID, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_ALREADY_PAID.getMessage());
        }
    }

    // =========================================================
    // completePayment() - 결제 완료 및 포인트 충전
    // =========================================================
    @Nested
    @DisplayName("completePayment() - 결제 완료 및 포인트 충전")
    class CompletePaymentTest {

        @Test
        @DisplayName("성공 - PENDING → COMPLETED 전환 + 포인트 충전 + Purchase 생성")
        void completePayment_success() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            Purchase mockPurchase = mock(Purchase.class);
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            Payment result = transactionService.completePayment(PAYMENT_ID, USER_ID, AMOUNT, PaymentMethod.CARD);

            // then
            assertThat(result).isNotNull();
            verify(mockPayment, times(1)).complete(PaymentMethod.CARD);
            verify(pointService, times(1)).charge(USER_ID, AMOUNT);
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
        }

        @Test
        @DisplayName("성공 - Purchase 생성 시 올바른 파라미터 전달")
        void completePayment_createsPurchaseWithCorrectParams() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            Purchase mockPurchase = mock(Purchase.class);
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.completePayment(PAYMENT_ID, USER_ID, AMOUNT, PaymentMethod.CARD);

            // then
            ArgumentCaptor<Purchase> purchaseCaptor = ArgumentCaptor.forClass(Purchase.class);
            verify(purchaseRepository).save(purchaseCaptor.capture());

            Purchase captured = purchaseCaptor.getValue();
            assertThat(captured.getUserId()).isEqualTo(USER_ID);
            assertThat(captured.getType()).isEqualTo(PurchaseType.POINT);
            assertThat(captured.getAmount()).isEqualTo(AMOUNT);
            assertThat(captured.getPaymentId()).isEqualTo(PAYMENT_ID);
        }

        @Test
        @DisplayName("실패 - Payment 없을 시 ERR_PAYMENT_NOT_FOUND")
        void completePayment_paymentNotFound_throwsException() {
            // given
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.completePayment(PAYMENT_ID, USER_ID, AMOUNT, PaymentMethod.CARD))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND.getMessage());

            verify(pointService, never()).charge(anyLong(), anyLong());
            verify(purchaseRepository, never()).save(any());
        }

        @Test
        @DisplayName("방어 코드 - 이미 COMPLETED 상태일 때 재처리 스킵 (멱등성)")
        void completePayment_alreadyCompleted_skips() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.COMPLETED);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            Payment result = transactionService.completePayment(PAYMENT_ID, USER_ID, AMOUNT, PaymentMethod.CARD);

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(mockPayment, never()).complete(any());
            verify(pointService, never()).charge(anyLong(), anyLong());
            verify(purchaseRepository, never()).save(any());
        }

        @Test
        @DisplayName("성공 - FAILED → COMPLETED 전환 + 포인트 충전 (confirm 타임아웃 후 재검증 케이스)")
        void completePayment_failedPayment_completesSuccessfully() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.FAILED);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mock(Purchase.class));

            // when
            Payment result = transactionService.completePayment(PAYMENT_ID, USER_ID, AMOUNT, PaymentMethod.CARD);

            // then
            assertThat(result).isNotNull();
            verify(mockPayment, times(1)).complete(PaymentMethod.CARD);
            verify(pointService, times(1)).charge(USER_ID, AMOUNT);
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
        }

        @Test
        @DisplayName("방어 코드 - REFUNDED 상태는 처리 불가, 스킵")
        void completePayment_refundedPayment_skips() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.REFUNDED);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            Payment result = transactionService.completePayment(PAYMENT_ID, USER_ID, AMOUNT, PaymentMethod.CARD);

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(mockPayment, never()).complete(any());
            verify(pointService, never()).charge(anyLong(), anyLong());
            verify(purchaseRepository, never()).save(any());
        }

        @Test
        @DisplayName("검증 - 실행 순서: complete() → charge() → save(Purchase)")
        void completePayment_executionOrder() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            Purchase mockPurchase = mock(Purchase.class);
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.completePayment(PAYMENT_ID, USER_ID, AMOUNT, PaymentMethod.CARD);

            // then
            var inOrder = inOrder(mockPayment, pointService, purchaseRepository);
            inOrder.verify(mockPayment).complete(PaymentMethod.CARD);
            inOrder.verify(pointService).charge(USER_ID, AMOUNT);
            inOrder.verify(purchaseRepository).save(any(Purchase.class));
        }
    }

    // =========================================================
    // failPayment() - 결제 실패 처리
    // =========================================================
    @Nested
    @DisplayName("failPayment() - 결제 실패 처리")
    class FailPaymentTest {

        @Test
        @DisplayName("성공 - PENDING → FAILED 전환")
        void failPayment_success() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            transactionService.failPayment(PAYMENT_ID);

            // then
            verify(mockPayment, times(1)).fail();
        }

        @Test
        @DisplayName("성공 - Payment 없을 시 예외 없이 종료")
        void failPayment_paymentNotFound_noException() {
            // given
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

            // when & then (예외 없어야 함)
            transactionService.failPayment(PAYMENT_ID);

            verify(paymentRepository, times(1)).findById(PAYMENT_ID);
        }

        @Test
        @DisplayName("방어 코드 - 이미 COMPLETED 상태일 때 전환 스킵")
        void failPayment_alreadyCompleted_skips() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.COMPLETED);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            transactionService.failPayment(PAYMENT_ID);

            // then
            verify(mockPayment, never()).fail();
        }

        @Test
        @DisplayName("방어 코드 - 이미 REFUNDED 상태일 때 전환 스킵")
        void failPayment_alreadyRefunded_skips() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.REFUNDED);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            transactionService.failPayment(PAYMENT_ID);

            // then
            verify(mockPayment, never()).fail();
        }

        @Test
        @DisplayName("방어 코드 - 이미 FAILED 상태일 때 전환 스킵")
        void failPayment_alreadyFailed_skips() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.FAILED);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            transactionService.failPayment(PAYMENT_ID);

            // then
            verify(mockPayment, never()).fail();
        }
    }

    // =========================================================
    // getPaymentForCancel() - 환불 대상 검증
    // =========================================================
    @Nested
    @DisplayName("getPaymentForCancel() - 환불 대상 검증")
    class GetPaymentForCancelTest {

        @Test
        @DisplayName("성공 - COMPLETED 상태의 본인 결제 반환")
        void getPaymentForCancel_success() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.COMPLETED);
            given(paymentRepository.findByIdAndUserId(PAYMENT_ID, USER_ID)).willReturn(Optional.of(mockPayment));

            // when
            Payment result = transactionService.getPaymentForCancel(USER_ID, PAYMENT_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 결제 시 ERR_PAYMENT_NOT_FOUND")
        void getPaymentForCancel_paymentNotFound_throwsException() {
            // given
            given(paymentRepository.findByIdAndUserId(PAYMENT_ID, USER_ID)).willReturn(Optional.empty());
            given(paymentRepository.existsById(PAYMENT_ID)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> transactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 다른 사용자 결제 접근 시 ERR_NOT_MY_ORDER")
        void getPaymentForCancel_notMyOrder_throwsException() {
            // given
            given(paymentRepository.findByIdAndUserId(PAYMENT_ID, USER_ID)).willReturn(Optional.empty());
            given(paymentRepository.existsById(PAYMENT_ID)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> transactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_NOT_MY_ORDER.getMessage());
        }

        @Test
        @DisplayName("실패 - PENDING 상태 결제는 환불 불가")
        void getPaymentForCancel_pendingStatus_throwsException() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.findByIdAndUserId(PAYMENT_ID, USER_ID)).willReturn(Optional.of(mockPayment));

            // when & then
            assertThatThrownBy(() -> transactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_INVALID_COMPLETE.getMessage());
        }

        @Test
        @DisplayName("실패 - FAILED 상태 결제는 환불 불가")
        void getPaymentForCancel_failedStatus_throwsException() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.FAILED);
            given(paymentRepository.findByIdAndUserId(PAYMENT_ID, USER_ID)).willReturn(Optional.of(mockPayment));

            // when & then
            assertThatThrownBy(() -> transactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_INVALID_COMPLETE.getMessage());
        }

        @Test
        @DisplayName("실패 - REFUNDED 상태 결제는 환불 불가")
        void getPaymentForCancel_refundedStatus_throwsException() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.REFUNDED);
            given(paymentRepository.findByIdAndUserId(PAYMENT_ID, USER_ID)).willReturn(Optional.of(mockPayment));

            // when & then
            assertThatThrownBy(() -> transactionService.getPaymentForCancel(USER_ID, PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_INVALID_COMPLETE.getMessage());
        }
    }

    // =========================================================
    // finalizeCancel() - 환불 완료 처리
    // =========================================================
    @Nested
    @DisplayName("finalizeCancel() - 환불 완료 처리")
    class FinalizeCancelTest {

        @Test
        @DisplayName("성공 - COMPLETED → REFUNDED 전환")
        void finalizeCancel_success() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.COMPLETED);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            Payment result = transactionService.finalizeCancel(PAYMENT_ID);

            // then
            assertThat(result).isNotNull();
            verify(mockPayment, times(1)).cancel();
        }

        @Test
        @DisplayName("실패 - Payment 없을 시 ERR_PAYMENT_NOT_FOUND")
        void finalizeCancel_paymentNotFound_throwsException() {
            // given
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.finalizeCancel(PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("방어 코드 - 이미 REFUNDED 상태일 때 전환 스킵")
        void finalizeCancel_alreadyRefunded_skips() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.REFUNDED);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            Payment result = transactionService.finalizeCancel(PAYMENT_ID);

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(mockPayment, never()).cancel();
        }

        @Test
        @DisplayName("방어 코드 - PENDING 상태일 때 전환 스킵")
        void finalizeCancel_pendingStatus_skips() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.PENDING);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            Payment result = transactionService.finalizeCancel(PAYMENT_ID);

            // then
            verify(mockPayment, never()).cancel();
        }

        @Test
        @DisplayName("방어 코드 - FAILED 상태일 때 전환 스킵")
        void finalizeCancel_failedStatus_skips() {
            // given
            Payment mockPayment = createMockPayment(PAYMENT_ID, USER_ID, PAYMENT_KEY, AMOUNT, PaymentStatus.FAILED);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(mockPayment));

            // when
            Payment result = transactionService.finalizeCancel(PAYMENT_ID);

            // then
            verify(mockPayment, never()).cancel();
        }
    }
}
