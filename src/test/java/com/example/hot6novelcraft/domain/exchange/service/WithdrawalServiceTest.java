package com.example.hot6novelcraft.domain.exchange.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.exchange.dto.request.WithdrawalCreateRequest;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalDetailResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.WithdrawalResponse;
import com.example.hot6novelcraft.domain.exchange.entity.BankAccount;
import com.example.hot6novelcraft.domain.exchange.entity.Revenue;
import com.example.hot6novelcraft.domain.exchange.entity.Withdrawal;
import com.example.hot6novelcraft.domain.exchange.entity.enums.RevenueType;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import com.example.hot6novelcraft.domain.exchange.repository.BankAccountRepository;
import com.example.hot6novelcraft.domain.exchange.repository.RevenueRepository;
import com.example.hot6novelcraft.domain.exchange.repository.WithdrawalRepository;
import com.example.hot6novelcraft.domain.exchange.util.AesEncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @InjectMocks WithdrawalService withdrawalService;
    @Mock WithdrawalRepository withdrawalRepository;
    @Mock RevenueRepository revenueRepository;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock AesEncryptionUtil aesEncryptionUtil;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOperations;
    @Mock RevenueService revenueService;
    @Mock StatisticsService statisticsService;

    private final Long AUTHOR_ID = 1L;

    private BankAccount createVerifiedAccount() {
        BankAccount account = BankAccount.create(AUTHOR_ID, "국민은행", "encrypted", "홍길동");
        ReflectionTestUtils.setField(account, "id", 1L);
        account.verify();
        return account;
    }

    private Withdrawal createWithdrawal(Long id, WithdrawalStatus status) {
        Withdrawal withdrawal = Withdrawal.request(AUTHOR_ID, 1L, 50000, 5000);
        ReflectionTestUtils.setField(withdrawal, "id", id);
        if (status == WithdrawalStatus.COMPLETED) {
            withdrawal.processing();
            withdrawal.complete();
        }
        if (status == WithdrawalStatus.REJECTED) withdrawal.reject("테스트 거절");
        return withdrawal;
    }

    @Nested
    @DisplayName("환전 신청")
    class CreateWithdrawal {

        @Test
        @DisplayName("성공 - 정상 환전 신청")
        void success() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(bankAccountRepository.findByUserIdAndIsVerifiedTrue(AUTHOR_ID))
                    .willReturn(Optional.of(createVerifiedAccount()));
            given(withdrawalRepository.existsByAuthorIdAndStatus(AUTHOR_ID, WithdrawalStatus.PENDING)).willReturn(false);
            given(revenueRepository.sumAmountByAuthorIdAndTypeIn(eq(AUTHOR_ID), any())).willReturn(100000);
            given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.WITHDRAWAL)).willReturn(0);

            Withdrawal saved = Withdrawal.request(AUTHOR_ID, 1L, 50000, 5000);
            ReflectionTestUtils.setField(saved, "id", 1L);
            given(withdrawalRepository.save(any())).willReturn(saved);
            given(revenueRepository.save(any())).willReturn(Revenue.ofWithdrawal(AUTHOR_ID, 50000, 50000));

            WithdrawalResponse res = withdrawalService.createWithdrawal(AUTHOR_ID, new WithdrawalCreateRequest(50000));

            assertThat(res.requestAmount()).isEqualTo(50000);
            assertThat(res.fee()).isEqualTo(5000);
            assertThat(res.actualAmount()).isEqualTo(45000);
            assertThat(res.status()).isEqualTo(WithdrawalStatus.PENDING);
        }

        @Test
        @DisplayName("실패 - 분산락 획득 실패")
        void failLock() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).willReturn(false);
            assertThatThrownBy(() -> withdrawalService.createWithdrawal(AUTHOR_ID, new WithdrawalCreateRequest(50000)))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 인증된 계좌 없음")
        void failNoAccount() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(bankAccountRepository.findByUserIdAndIsVerifiedTrue(AUTHOR_ID)).willReturn(Optional.empty());
            assertThatThrownBy(() -> withdrawalService.createWithdrawal(AUTHOR_ID, new WithdrawalCreateRequest(50000)))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - PENDING 건 중복")
        void failPendingExists() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(bankAccountRepository.findByUserIdAndIsVerifiedTrue(AUTHOR_ID))
                    .willReturn(Optional.of(createVerifiedAccount()));
            given(withdrawalRepository.existsByAuthorIdAndStatus(AUTHOR_ID, WithdrawalStatus.PENDING)).willReturn(true);
            assertThatThrownBy(() -> withdrawalService.createWithdrawal(AUTHOR_ID, new WithdrawalCreateRequest(50000)))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 최소 금액 미만")
        void failBelowMinimum() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(bankAccountRepository.findByUserIdAndIsVerifiedTrue(AUTHOR_ID))
                    .willReturn(Optional.of(createVerifiedAccount()));
            given(withdrawalRepository.existsByAuthorIdAndStatus(AUTHOR_ID, WithdrawalStatus.PENDING)).willReturn(false);
            assertThatThrownBy(() -> withdrawalService.createWithdrawal(AUTHOR_ID, new WithdrawalCreateRequest(5000)))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 잔액 부족 시 정확한 에러 코드 반환")
        void failInsufficientBalance() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(bankAccountRepository.findByUserIdAndIsVerifiedTrue(AUTHOR_ID)).willReturn(Optional.of(createVerifiedAccount()));
            given(revenueRepository.sumAmountByAuthorIdAndTypeIn(eq(AUTHOR_ID), any())).willReturn(10000);
            given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.WITHDRAWAL)).willReturn(0);

            assertThatThrownBy(() -> withdrawalService.createWithdrawal(AUTHOR_ID, new WithdrawalCreateRequest(50000)))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasFieldOrPropertyWithValue("errorCode", com.example.hot6novelcraft.domain.exchange.exception.ExchangeExceptionEnum.WITHDRAWAL_INSUFFICIENT_BALANCE);
        }
    }

    @Nested
    @DisplayName("환전 상세 조회")
    class GetDetail {

        @Test
        @DisplayName("성공 - 환전 상세 + 계좌 정보")
        void success() {
            Withdrawal withdrawal = createWithdrawal(1L, WithdrawalStatus.PENDING);
            BankAccount account = createVerifiedAccount();
            given(withdrawalRepository.findByIdAndAuthorId(1L, AUTHOR_ID)).willReturn(Optional.of(withdrawal));
            given(bankAccountRepository.findById(1L)).willReturn(Optional.of(account));
            given(aesEncryptionUtil.decrypt("encrypted")).willReturn("1234567890");

            WithdrawalDetailResponse res = withdrawalService.getWithdrawalDetail(AUTHOR_ID, 1L);

            assertThat(res.withdrawalId()).isEqualTo(1L);
            assertThat(res.bankAccount().maskedAccountNumber()).isEqualTo("******7890");
        }

        @Test
        @DisplayName("실패 - 환전 건 없음")
        void failNotFound() {
            given(withdrawalRepository.findByIdAndAuthorId(999L, AUTHOR_ID)).willReturn(Optional.empty());
            assertThatThrownBy(() -> withdrawalService.getWithdrawalDetail(AUTHOR_ID, 999L))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    @Nested
    @DisplayName("관리자 환전 승인")
    class Approve {

        @Test
        @DisplayName("성공 - PENDING → COMPLETED")
        void success() {
            Withdrawal withdrawal = createWithdrawal(1L, WithdrawalStatus.PENDING);
            given(withdrawalRepository.findById(1L)).willReturn(Optional.of(withdrawal));

            WithdrawalResponse res = withdrawalService.approveWithdrawal(1L);

            assertThat(res.status()).isEqualTo(WithdrawalStatus.COMPLETED);
            verify(revenueService).evictRevenueOverviewCache(AUTHOR_ID);
            verify(statisticsService).evictStatisticsCache(AUTHOR_ID);
        }

        @Test
        @DisplayName("실패 - 환전 건 없음")
        void failNotFound() {
            given(withdrawalRepository.findById(999L)).willReturn(Optional.empty());
            assertThatThrownBy(() -> withdrawalService.approveWithdrawal(999L))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    @Nested
    @DisplayName("관리자 환전 거절")
    class Reject {

        @Test
        @DisplayName("성공 - PENDING → REJECTED + 잔액 복구 (REFUND 타입)")
        void success() {
            Withdrawal withdrawal = createWithdrawal(1L, WithdrawalStatus.PENDING);
            given(withdrawalRepository.findById(1L)).willReturn(Optional.of(withdrawal));
            given(revenueRepository.sumAmountByAuthorIdAndTypeIn(eq(AUTHOR_ID), any())).willReturn(100000);
            given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.WITHDRAWAL)).willReturn(50000);
            given(revenueRepository.save(any()))
                    .willReturn(Revenue.create(AUTHOR_ID, null, 50000, 100000, RevenueType.REFUND));

            WithdrawalResponse res = withdrawalService.rejectWithdrawal(1L, "서류 미비");

            assertThat(res.status()).isEqualTo(WithdrawalStatus.REJECTED);
            verify(revenueRepository).save(any());
            verify(revenueService).evictRevenueOverviewCache(AUTHOR_ID);
            verify(statisticsService).evictStatisticsCache(AUTHOR_ID);
        }

        @Test
        @DisplayName("실패 - 환전 건 없음")
        void failNotFound() {
            given(withdrawalRepository.findById(999L)).willReturn(Optional.empty());
            assertThatThrownBy(() -> withdrawalService.rejectWithdrawal(999L, "사유"))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }
}