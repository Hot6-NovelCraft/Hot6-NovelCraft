package com.example.hot6novelcraft.domain.payment.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.PaymentExceptionEnum;
import com.example.hot6novelcraft.domain.payment.entity.Payment;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentMethod;
import com.example.hot6novelcraft.domain.payment.entity.enums.PaymentStatus;
import com.example.hot6novelcraft.domain.payment.repository.PaymentRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import com.example.hot6novelcraft.domain.purchases.entity.Purchase;
import com.example.hot6novelcraft.domain.purchases.entity.enums.PurchaseType;
import com.example.hot6novelcraft.domain.purchases.repository.PurchaseRepository;
import com.example.hot6novelcraft.domain.subscription.entity.Subscription;
import com.example.hot6novelcraft.domain.subscription.repository.SubscriptionRepository;
import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEvent;
import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEventStatus;
import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEventType;
import com.example.hot6novelcraft.domain.webhookevent.repository.WebhookEventRepository;
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
@DisplayName("WebhookTransactionService 테스트")
class WebhookTransactionServiceTest {

    @InjectMocks
    private WebhookTransactionService transactionService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PointService pointService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private static final String TRANSACTION_ID = "transaction-12345";
    private static final String PAYMENT_KEY = "payment-test-key-12345";
    private static final Long PAYMENT_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final Long AMOUNT = 10000L;
    private static final Long WEBHOOK_EVENT_ID = 1000L;
    private static final String RAW_PAYLOAD = "{\"type\":\"Transaction.Paid\"}";

    private Payment createMockPayment(Long id, Long userId, PaymentStatus status) {
        Payment payment = mock(Payment.class);
        given(payment.getId()).willReturn(id);
        given(payment.getUserId()).willReturn(userId);
        given(payment.getAmount()).willReturn(AMOUNT);
        given(payment.getStatus()).willReturn(status);
        given(payment.getPaymentKey()).willReturn(PAYMENT_KEY);
        return payment;
    }

    private WebhookEvent createMockWebhookEvent(Long id, WebhookEventStatus status) {
        WebhookEvent event = mock(WebhookEvent.class);
        given(event.getId()).willReturn(id);
        given(event.getStatus()).willReturn(status);
        given(event.getWebhookId()).willReturn(TRANSACTION_ID);
        return event;
    }

    // =========================================================
    // prepareWebhookEvent() - 멱등성 체크 및 이벤트 생성
    // =========================================================
    @Nested
    @DisplayName("prepareWebhookEvent() - 멱등성 체크 및 이벤트 생성")
    class PrepareWebhookEventTest {

        @Test
        @DisplayName("성공 - 신규 이벤트 생성")
        void prepareWebhookEvent_newEvent_success() {
            // given
            given(webhookEventRepository.findByWebhookId(TRANSACTION_ID)).willReturn(Optional.empty());
            WebhookEvent newEvent = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            given(webhookEventRepository.save(any(WebhookEvent.class))).willReturn(newEvent);

            // when
            WebhookEvent result = transactionService.prepareWebhookEvent(
                    TRANSACTION_ID, WebhookEventType.TRANSACTION_PAID, PAYMENT_KEY, RAW_PAYLOAD
            );

            // then
            assertThat(result).isNotNull();
            verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
        }

        @Test
        @DisplayName("멱등성 - 이미 COMPLETE 상태면 null 반환 (처리 skip)")
        void prepareWebhookEvent_alreadyComplete_returnsNull() {
            // given
            WebhookEvent completeEvent = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.COMPLETE);
            given(webhookEventRepository.findByWebhookId(TRANSACTION_ID)).willReturn(Optional.of(completeEvent));

            // when
            WebhookEvent result = transactionService.prepareWebhookEvent(
                    TRANSACTION_ID, WebhookEventType.TRANSACTION_PAID, PAYMENT_KEY, RAW_PAYLOAD
            );

            // then
            assertThat(result).isNull();
            verify(webhookEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("재시도 - PENDING 상태면 기존 이벤트 반환")
        void prepareWebhookEvent_pendingEvent_returnsExisting() {
            // given
            WebhookEvent pendingEvent = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            given(webhookEventRepository.findByWebhookId(TRANSACTION_ID)).willReturn(Optional.of(pendingEvent));

            // when
            WebhookEvent result = transactionService.prepareWebhookEvent(
                    TRANSACTION_ID, WebhookEventType.TRANSACTION_PAID, PAYMENT_KEY, RAW_PAYLOAD
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(WEBHOOK_EVENT_ID);
            verify(webhookEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("재시도 - FAIL 상태면 기존 이벤트 반환")
        void prepareWebhookEvent_failedEvent_returnsExisting() {
            // given
            WebhookEvent failedEvent = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.FAIL);
            given(webhookEventRepository.findByWebhookId(TRANSACTION_ID)).willReturn(Optional.of(failedEvent));

            // when
            WebhookEvent result = transactionService.prepareWebhookEvent(
                    TRANSACTION_ID, WebhookEventType.TRANSACTION_PAID, PAYMENT_KEY, RAW_PAYLOAD
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(WEBHOOK_EVENT_ID);
            verify(webhookEventRepository, never()).save(any());
        }
    }

    // =========================================================
    // getPaymentByKey() - Payment 조회
    // =========================================================
    @Nested
    @DisplayName("getPaymentByKey() - Payment 조회")
    class GetPaymentByKeyTest {

        @Test
        @DisplayName("성공 - Payment 존재 시 반환")
        void getPaymentByKey_paymentExists_returnsPayment() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.PENDING);
            given(paymentRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(payment));

            // when
            Payment result = transactionService.getPaymentByKey(PAYMENT_KEY);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(PAYMENT_ID);
        }

        @Test
        @DisplayName("성공 - Payment 없을 시 null 반환")
        void getPaymentByKey_paymentNotFound_returnsNull() {
            // given
            given(paymentRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.empty());

            // when
            Payment result = transactionService.getPaymentByKey(PAYMENT_KEY);

            // then
            assertThat(result).isNull();
        }
    }

    // =========================================================
    // markEventComplete() - WebhookEvent COMPLETE 처리
    // =========================================================
    @Nested
    @DisplayName("markEventComplete() - WebhookEvent COMPLETE 처리")
    class MarkEventCompleteTest {

        @Test
        @DisplayName("성공 - WebhookEvent를 COMPLETE 상태로 전환")
        void markEventComplete_success() {
            // given
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.markEventComplete(WEBHOOK_EVENT_ID);

            // then
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("성공 - WebhookEvent 없어도 예외 없이 종료")
        void markEventComplete_eventNotFound_noException() {
            // given
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.empty());

            // when & then (예외 없어야 함)
            transactionService.markEventComplete(WEBHOOK_EVENT_ID);

            verify(webhookEventRepository, times(1)).findById(WEBHOOK_EVENT_ID);
        }
    }

    // =========================================================
    // markEventFailed() - WebhookEvent FAIL 처리
    // =========================================================
    @Nested
    @DisplayName("markEventFailed() - WebhookEvent FAIL 처리")
    class MarkEventFailedTest {

        @Test
        @DisplayName("성공 - WebhookEvent를 FAIL 상태로 전환")
        void markEventFailed_success() {
            // given
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            String reason = "PortOne API 조회 실패";

            // when
            transactionService.markEventFailed(WEBHOOK_EVENT_ID, reason);

            // then
            verify(event, times(1)).fail(reason);
        }

        @Test
        @DisplayName("성공 - WebhookEvent 없어도 예외 없이 종료")
        void markEventFailed_eventNotFound_noException() {
            // given
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.empty());

            // when & then (예외 없어야 함)
            transactionService.markEventFailed(WEBHOOK_EVENT_ID, "Test reason");

            verify(webhookEventRepository, times(1)).findById(WEBHOOK_EVENT_ID);
        }
    }

    // =========================================================
    // failPendingPayment() - PENDING Payment 실패 처리
    // =========================================================
    @Nested
    @DisplayName("failPendingPayment() - PENDING Payment 실패 처리")
    class FailPendingPaymentTest {

        @Test
        @DisplayName("성공 - PENDING Payment를 FAILED로 전환 + Event COMPLETE")
        void failPendingPayment_success() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.PENDING);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.failPendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID);

            // then
            verify(payment, times(1)).fail();
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("방어 코드 - Payment가 PENDING이 아니면 fail() 호출 안 함")
        void failPendingPayment_notPending_skipsFailure() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.COMPLETED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.failPendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID);

            // then
            verify(payment, never()).fail();
            verify(event, times(1)).complete(); // Event는 여전히 COMPLETE 처리
        }

        @Test
        @DisplayName("성공 - Payment 없어도 Event는 COMPLETE 처리")
        void failPendingPayment_paymentNotFound_stillCompletesEvent() {
            // given
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.failPendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID);

            // then
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("검증 - 실행 순서: payment.fail() → event.complete()")
        void failPendingPayment_executionOrder() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.PENDING);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.failPendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID);

            // then
            var inOrder = inOrder(payment, event);
            inOrder.verify(payment).fail();
            inOrder.verify(event).complete();
        }
    }

    // =========================================================
    // completePendingPayment() - PENDING Payment 보정
    // =========================================================
    @Nested
    @DisplayName("completePendingPayment() - PENDING Payment 보정")
    class CompletePendingPaymentTest {

        @Test
        @DisplayName("성공 - PENDING Payment를 COMPLETED로 전환 + 포인트 충전 + Purchase 생성 + Event COMPLETE")
        void completePendingPayment_success() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.PENDING);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            Purchase mockPurchase = mock(Purchase.class);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.completePendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD);

            // then
            verify(payment, times(1)).complete(PaymentMethod.CARD);
            verify(pointService, times(1)).charge(USER_ID, AMOUNT);
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("실패 - Payment 없을 시 ERR_PAYMENT_NOT_FOUND")
        void completePendingPayment_paymentNotFound_throwsException() {
            // given
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.completePendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND.getMessage());

            verify(pointService, never()).charge(anyLong(), anyLong());
            verify(purchaseRepository, never()).save(any());
        }

        @Test
        @DisplayName("방어 코드 - 이미 COMPLETED 상태이면 보정 스킵 + Event만 COMPLETE")
        void completePendingPayment_alreadyCompleted_skipsCompensation() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.COMPLETED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.completePendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD);

            // then
            verify(payment, never()).complete(any());
            verify(pointService, never()).charge(anyLong(), anyLong());
            verify(purchaseRepository, never()).save(any());
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("성공 - FAILED Payment도 COMPLETED로 보정 (confirm 타임아웃 케이스)")
        void completePendingPayment_failedPayment_completesSuccessfully() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.FAILED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            Purchase mockPurchase = mock(Purchase.class);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.completePendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD);

            // then
            verify(payment, times(1)).complete(PaymentMethod.CARD);
            verify(pointService, times(1)).charge(USER_ID, AMOUNT);
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("방어 코드 - REFUNDED 상태는 보정 불가, Event만 COMPLETE")
        void completePendingPayment_refundedPayment_skipsCompensation() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.REFUNDED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.completePendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD);

            // then
            verify(payment, never()).complete(any());
            verify(pointService, never()).charge(anyLong(), anyLong());
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("검증 - 실행 순서: complete() → charge() → save(Purchase) → event.complete()")
        void completePendingPayment_executionOrder() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.PENDING);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            Purchase mockPurchase = mock(Purchase.class);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.completePendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD);

            // then
            var inOrder = inOrder(payment, pointService, purchaseRepository, event);
            inOrder.verify(payment).complete(PaymentMethod.CARD);
            inOrder.verify(pointService).charge(USER_ID, AMOUNT);
            inOrder.verify(purchaseRepository).save(any(Purchase.class));
            inOrder.verify(event).complete();
        }

        @Test
        @DisplayName("성공 - Purchase 생성 시 올바른 파라미터 전달")
        void completePendingPayment_createsPurchaseWithCorrectParams() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.PENDING);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            Purchase mockPurchase = mock(Purchase.class);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.completePendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD);

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
        @DisplayName("방어 코드 - WebhookEvent 없어도 예외 없이 처리")
        void completePendingPayment_eventNotFound_noException() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.PENDING);
            Purchase mockPurchase = mock(Purchase.class);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.empty());
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when & then (예외 없어야 함)
            transactionService.completePendingPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD);

            verify(payment, times(1)).complete(PaymentMethod.CARD);
            verify(pointService, times(1)).charge(USER_ID, AMOUNT);
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
        }
    }

    // =========================================================
    // finalizeRefundFromWebhook() - 환불 타임아웃 보정
    // =========================================================
    @Nested
    @DisplayName("finalizeRefundFromWebhook() - 환불 타임아웃 보정")
    class FinalizeRefundFromWebhookTest {

        @Test
        @DisplayName("성공 - COMPLETED → REFUNDED 전환 + 포인트 차감 (compensateDeduct 실행된 케이스)")
        void finalizeRefundFromWebhook_pointsAvailable_deductsAndRefunds() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.COMPLETED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            // pointService.deduct() 성공 (잔액 충분)

            // when
            transactionService.finalizeRefundFromWebhook(WEBHOOK_EVENT_ID, PAYMENT_ID);

            // then
            verify(pointService, times(1)).deduct(USER_ID, AMOUNT);
            verify(payment, times(1)).cancel();
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("실패 - ERR_INSUFFICIENT_POINT 발생 시 이벤트 FAIL + payment.cancel() 미호출 (수동 보정 필요)")
        void finalizeRefundFromWebhook_insufficientPoint_marksEventFailedAndAborts() {
            // given — compensateDeduct 실행 후 사용자가 복원 포인트를 소비한 케이스와 구분 불가
            //         단정하고 cancel()까지 가면 환불 금액 + 포인트 소비를 둘 다 취득하는 손실 발생
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.COMPLETED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            doThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT))
                    .when(pointService).deduct(USER_ID, AMOUNT);

            // when
            transactionService.finalizeRefundFromWebhook(WEBHOOK_EVENT_ID, PAYMENT_ID);

            // then — REFUNDED로 바꾸면 안 되고, 이벤트는 FAIL로 남겨 수동 보정 경로로 보내야 함
            verify(payment, never()).cancel();
            verify(event, times(1)).fail(PaymentExceptionEnum.ERR_INSUFFICIENT_POINT.getMessage());
        }

        @Test
        @DisplayName("방어 코드 - COMPLETED가 아닌 상태면 보정 스킵 + Event COMPLETE")
        void finalizeRefundFromWebhook_notCompleted_skips() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.REFUNDED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.finalizeRefundFromWebhook(WEBHOOK_EVENT_ID, PAYMENT_ID);

            // then
            verify(pointService, never()).deduct(anyLong(), anyLong());
            verify(payment, never()).cancel();
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("실패 - Payment 없을 시 ERR_PAYMENT_NOT_FOUND")
        void finalizeRefundFromWebhook_paymentNotFound_throwsException() {
            // given
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.finalizeRefundFromWebhook(WEBHOOK_EVENT_ID, PAYMENT_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND.getMessage());

            verify(pointService, never()).deduct(anyLong(), anyLong());
        }

        @Test
        @DisplayName("실패 - ERR_POINT_NOT_FOUND 발생 시 이벤트 FAIL + payment.cancel() 미호출 (데이터 정합성 오류)")
        void finalizeRefundFromWebhook_pointNotFound_marksEventFailedAndAborts() {
            // given — 포인트 엔티티 유실 등 데이터 정합성 이슈
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.COMPLETED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            doThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_POINT_NOT_FOUND))
                    .when(pointService).deduct(USER_ID, AMOUNT);

            // when
            transactionService.finalizeRefundFromWebhook(WEBHOOK_EVENT_ID, PAYMENT_ID);

            // then — REFUNDED로 바꾸면 안 되고, 이벤트는 FAIL로 남겨야 함
            verify(payment, never()).cancel();
            verify(event, times(1)).fail(PaymentExceptionEnum.ERR_POINT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("검증 - 실행 순서: deduct() → cancel() → event.complete()")
        void finalizeRefundFromWebhook_executionOrder() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.COMPLETED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.finalizeRefundFromWebhook(WEBHOOK_EVENT_ID, PAYMENT_ID);

            // then
            var inOrder = inOrder(pointService, payment, event);
            inOrder.verify(pointService).deduct(USER_ID, AMOUNT);
            inOrder.verify(payment).cancel();
            inOrder.verify(event).complete();
        }
    }

    // =========================================================
    // completePendingSubscriptionPayment() - 구독 결제 보정
    // =========================================================
    @Nested
    @DisplayName("completePendingSubscriptionPayment() - 구독 결제 보정")
    class CompletePendingSubscriptionPaymentTest {

        private static final Long SUBSCRIPTION_ID = 500L;

        @Test
        @DisplayName("성공 - PENDING 구독 결제를 COMPLETED로 전환 + Purchase(SUBSCRIPTION) 생성 + Subscription 업데이트")
        void completePendingSubscriptionPayment_pending_completesSuccessfully() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.PENDING);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            Subscription subscription = mock(Subscription.class);
            Purchase mockPurchase = mock(Purchase.class);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            given(subscriptionRepository.findById(SUBSCRIPTION_ID)).willReturn(Optional.of(subscription));
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.completePendingSubscriptionPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD, SUBSCRIPTION_ID);

            // then
            verify(payment, times(1)).complete(PaymentMethod.CARD);
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
            verify(subscription, times(1)).updateAfterPayment(PAYMENT_ID);
            verify(subscriptionRepository, times(1)).save(subscription);
            verify(event, times(1)).complete();
            verify(pointService, never()).charge(anyLong(), anyLong()); // 구독 결제는 포인트 충전 없음
        }

        @Test
        @DisplayName("성공 - FAILED 구독 결제도 COMPLETED로 보정 (confirm 타임아웃 케이스)")
        void completePendingSubscriptionPayment_failed_completesSuccessfully() {
            // given — confirm 타임아웃으로 FAILED 처리됐으나 포트원에서는 실제로 결제 완료된 구독 건
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.FAILED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            Subscription subscription = mock(Subscription.class);
            Purchase mockPurchase = mock(Purchase.class);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            given(subscriptionRepository.findById(SUBSCRIPTION_ID)).willReturn(Optional.of(subscription));
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.completePendingSubscriptionPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD, SUBSCRIPTION_ID);

            // then — 구독 갱신과 구매 이력 모두 정상 처리 (이전엔 이 경우 누락됐음)
            verify(payment, times(1)).complete(PaymentMethod.CARD);
            verify(purchaseRepository, times(1)).save(any(Purchase.class));
            verify(subscription, times(1)).updateAfterPayment(PAYMENT_ID);
            verify(subscriptionRepository, times(1)).save(subscription);
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("방어 코드 - 이미 COMPLETED 상태이면 보정 스킵 + Event만 COMPLETE (멱등성)")
        void completePendingSubscriptionPayment_alreadyCompleted_skips() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.COMPLETED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.completePendingSubscriptionPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD, SUBSCRIPTION_ID);

            // then
            verify(payment, never()).complete(any());
            verify(purchaseRepository, never()).save(any());
            verify(subscriptionRepository, never()).save(any());
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("방어 코드 - REFUNDED 상태는 보정 불가, Event만 COMPLETE")
        void completePendingSubscriptionPayment_refunded_skips() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.REFUNDED);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));

            // when
            transactionService.completePendingSubscriptionPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD, SUBSCRIPTION_ID);

            // then
            verify(payment, never()).complete(any());
            verify(purchaseRepository, never()).save(any());
            verify(event, times(1)).complete();
        }

        @Test
        @DisplayName("실패 - Payment 없을 시 ERR_PAYMENT_NOT_FOUND")
        void completePendingSubscriptionPayment_paymentNotFound_throwsException() {
            // given
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> transactionService.completePendingSubscriptionPayment(
                    WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD, SUBSCRIPTION_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_NOT_FOUND.getMessage());

            verify(purchaseRepository, never()).save(any());
            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("검증 - 실행 순서: complete() → save(Purchase) → subscription.update() → event.complete()")
        void completePendingSubscriptionPayment_executionOrder() {
            // given
            Payment payment = createMockPayment(PAYMENT_ID, USER_ID, PaymentStatus.PENDING);
            WebhookEvent event = createMockWebhookEvent(WEBHOOK_EVENT_ID, WebhookEventStatus.PENDING);
            Subscription subscription = mock(Subscription.class);
            Purchase mockPurchase = mock(Purchase.class);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(webhookEventRepository.findById(WEBHOOK_EVENT_ID)).willReturn(Optional.of(event));
            given(subscriptionRepository.findById(SUBSCRIPTION_ID)).willReturn(Optional.of(subscription));
            given(purchaseRepository.save(any(Purchase.class))).willReturn(mockPurchase);

            // when
            transactionService.completePendingSubscriptionPayment(WEBHOOK_EVENT_ID, PAYMENT_ID, PaymentMethod.CARD, SUBSCRIPTION_ID);

            // then
            var inOrder = inOrder(payment, purchaseRepository, subscriptionRepository, event);
            inOrder.verify(payment).complete(PaymentMethod.CARD);
            inOrder.verify(purchaseRepository).save(any(Purchase.class));
            inOrder.verify(subscriptionRepository).save(subscription);
            inOrder.verify(event).complete();
        }
    }
}
