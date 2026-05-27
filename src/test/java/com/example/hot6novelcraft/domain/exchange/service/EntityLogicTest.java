package com.example.hot6novelcraft.domain.exchange.service;

import com.example.hot6novelcraft.domain.exchange.entity.AccountVerification;
import com.example.hot6novelcraft.domain.exchange.entity.BankAccount;
import com.example.hot6novelcraft.domain.exchange.entity.Revenue;
import com.example.hot6novelcraft.domain.exchange.entity.Withdrawal;
import com.example.hot6novelcraft.domain.exchange.entity.enums.FeePolicy;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class EntityLogicTest {

    @Nested
    @DisplayName("WithdrawalStatus 상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("PENDING → PROCESSING 가능")
        void pendingToProcessing() {
            assertThat(WithdrawalStatus.PENDING.canTransitionTo(WithdrawalStatus.PROCESSING)).isTrue();
        }

        @Test
        @DisplayName("PENDING → REJECTED 가능")
        void pendingToRejected() {
            assertThat(WithdrawalStatus.PENDING.canTransitionTo(WithdrawalStatus.REJECTED)).isTrue();
        }

        @Test
        @DisplayName("PENDING → COMPLETED 불가")
        void pendingToCompleted() {
            assertThat(WithdrawalStatus.PENDING.canTransitionTo(WithdrawalStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("PROCESSING → COMPLETED 가능")
        void processingToCompleted() {
            assertThat(WithdrawalStatus.PROCESSING.canTransitionTo(WithdrawalStatus.COMPLETED)).isTrue();
        }

        @Test
        @DisplayName("COMPLETED → 어디로든 전이 불가")
        void completedNoTransition() {
            assertThat(WithdrawalStatus.COMPLETED.canTransitionTo(WithdrawalStatus.PENDING)).isFalse();
            assertThat(WithdrawalStatus.COMPLETED.canTransitionTo(WithdrawalStatus.REJECTED)).isFalse();
        }

        @Test
        @DisplayName("REJECTED → 어디로든 전이 불가")
        void rejectedNoTransition() {
            assertThat(WithdrawalStatus.REJECTED.canTransitionTo(WithdrawalStatus.PENDING)).isFalse();
            assertThat(WithdrawalStatus.REJECTED.canTransitionTo(WithdrawalStatus.COMPLETED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Withdrawal 엔티티 상태 변경")
    class WithdrawalEntity {

        @Test
        @DisplayName("complete() - PENDING에서 바로 호출하면 에러 (PROCESSING 거쳐야 함)")
        void completeFromPending() {
            Withdrawal withdrawal = Withdrawal.request(1L, 1L, 50000, 1650);
            assertThatThrownBy(withdrawal::complete)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("processing() → complete() 정상 전이")
        void processingThenComplete() {
            Withdrawal withdrawal = Withdrawal.request(1L, 1L, 50000, 1650);
            withdrawal.processing();
            withdrawal.complete();
            assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.COMPLETED);
            assertThat(withdrawal.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("reject() - 거절 사유 기록")
        void reject() {
            Withdrawal withdrawal = Withdrawal.request(1L, 1L, 50000, 1650);
            withdrawal.reject("서류 미비");
            assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.REJECTED);
            assertThat(withdrawal.getRejectedReason()).isEqualTo("서류 미비");
            assertThat(withdrawal.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("COMPLETED에서 reject() 호출하면 에러")
        void rejectFromCompleted() {
            Withdrawal withdrawal = Withdrawal.request(1L, 1L, 50000, 1650);
            withdrawal.processing();
            withdrawal.complete();
            assertThatThrownBy(() -> withdrawal.reject("사유"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("actualAmount = requestAmount - fee")
        void actualAmount() {
            Withdrawal withdrawal = Withdrawal.request(1L, 1L, 50000, 5000);
            assertThat(withdrawal.getActualAmount()).isEqualTo(45000);
        }
    }

    @Nested
    @DisplayName("FeePolicy 수수료 계산")
    class Fee {

        @Test
        @DisplayName("10% 수수료 계산")
        void calculateFee() {
            assertThat(FeePolicy.DEFAULT.calculateFee(50000)).isEqualTo(5000);   // 50000 * 0.10
            assertThat(FeePolicy.DEFAULT.calculateFee(100000)).isEqualTo(10000); // 100000 * 0.10
            assertThat(FeePolicy.DEFAULT.calculateFee(10000)).isEqualTo(1000);   // 10000 * 0.10
        }

        @Test
        @DisplayName("실수령 금액 계산")
        void calculateActualAmount() {
            assertThat(FeePolicy.DEFAULT.calculateActualAmount(50000)).isEqualTo(45000);
        }

        @Test
        @DisplayName("최소 환전 금액 확인")
        void minimumAmount() {
            assertThat(FeePolicy.DEFAULT.getMinimumWithdrawalAmount()).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("AccountVerification 인증 로직")
    class Verification {

        @Test
        @DisplayName("생성 시 미인증 상태, 시도횟수 0, 5분 후 만료")
        void create() {
            AccountVerification v = AccountVerification.create(1L, "1234");
            assertThat(v.getIsVerified()).isFalse();
            assertThat(v.getAttemptCount()).isEqualTo(0);
            assertThat(v.getExpiredAt()).isAfter(LocalDateTime.now().plusMinutes(4));
        }

        @Test
        @DisplayName("만료 여부 확인")
        void isExpired() {
            AccountVerification v = AccountVerification.create(1L, "1234");
            assertThat(v.isExpired()).isFalse();

            ReflectionTestUtils.setField(v, "expiredAt", LocalDateTime.now().minusMinutes(1));
            assertThat(v.isExpired()).isTrue();
        }

        @Test
        @DisplayName("시도 횟수 초과 확인")
        void maxAttempt() {
            AccountVerification v = AccountVerification.create(1L, "1234");
            assertThat(v.isMaxAttemptExceeded()).isFalse();

            ReflectionTestUtils.setField(v, "attemptCount", 5);
            assertThat(v.isMaxAttemptExceeded()).isTrue();
        }

        @Test
        @DisplayName("시도 횟수 증가")
        void increaseAttempt() {
            AccountVerification v = AccountVerification.create(1L, "1234");
            v.increaseAttemptCount();
            assertThat(v.getAttemptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("인증 완료 처리")
        void verify() {
            AccountVerification v = AccountVerification.create(1L, "1234");
            v.verify();
            assertThat(v.getIsVerified()).isTrue();
        }
    }

    @Nested
    @DisplayName("BankAccount 엔티티")
    class BankAccountEntity {

        @Test
        @DisplayName("생성 시 미인증 상태")
        void create() {
            BankAccount account = BankAccount.create(1L, "국민은행", "encrypted", "홍길동");
            assertThat(account.getIsVerified()).isFalse();
        }

        @Test
        @DisplayName("인증 완료 처리")
        void verifyAccount() {
            BankAccount account = BankAccount.create(1L, "국민은행", "encrypted", "홍길동");
            account.verify();
            assertThat(account.getIsVerified()).isTrue();
        }

        @Test
        @DisplayName("계좌번호 마스킹 - 뒤 4자리만 노출")
        void masking() {
            BankAccount account = BankAccount.create(1L, "국민은행", "encrypted", "홍길동");
            assertThat(account.getMaskedAccountNumber("1234567890")).isEqualTo("******7890");
        }

        @Test
        @DisplayName("계좌번호 마스킹 - 4자리 이하는 ****")
        void maskingShort() {
            BankAccount account = BankAccount.create(1L, "국민은행", "encrypted", "홍길동");
            assertThat(account.getMaskedAccountNumber("123")).isEqualTo("****");
            assertThat(account.getMaskedAccountNumber(null)).isEqualTo("****");
        }
    }

    @Nested
    @DisplayName("Revenue 엔티티")
    class RevenueEntity {

        @Test
        @DisplayName("환전 차감용 Revenue 생성")
        void ofWithdrawal() {
            Revenue revenue = Revenue.ofWithdrawal(1L, 50000, 50000);
            assertThat(revenue.getAuthorId()).isEqualTo(1L);
            assertThat(revenue.getAmount()).isEqualTo(50000);
            assertThat(revenue.getBalance()).isEqualTo(50000);
            assertThat(revenue.getType()).isEqualTo(com.example.hot6novelcraft.domain.exchange.entity.enums.RevenueType.WITHDRAWAL);
            assertThat(revenue.getEpisodeId()).isNull();
        }
    }
}