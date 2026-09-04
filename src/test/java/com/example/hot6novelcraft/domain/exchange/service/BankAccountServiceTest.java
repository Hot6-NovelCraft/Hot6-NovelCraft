package com.example.hot6novelcraft.domain.exchange.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.exchange.client.BankVerificationClient;
import com.example.hot6novelcraft.domain.exchange.dto.request.BankAccountCreateRequest;
import com.example.hot6novelcraft.domain.exchange.dto.request.VerificationConfirmRequest;
import com.example.hot6novelcraft.domain.exchange.dto.response.BankAccountVerifyResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.VerificationConfirmResponse;
import com.example.hot6novelcraft.domain.exchange.entity.AccountVerification;
import com.example.hot6novelcraft.domain.exchange.entity.BankAccount;
import com.example.hot6novelcraft.domain.exchange.repository.AccountVerificationRepository;
import com.example.hot6novelcraft.domain.exchange.repository.BankAccountRepository;
import com.example.hot6novelcraft.domain.exchange.util.AesEncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @InjectMocks BankAccountService bankAccountService;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock AccountVerificationRepository accountVerificationRepository;
    @Mock BankVerificationClient bankVerificationClient;
    @Mock AesEncryptionUtil aesEncryptionUtil;
    @Mock RevenueService revenueService;

    private final Long USER_ID = 1L;

    private BankAccount createAccount(Long id, Long userId, boolean verified) {
        BankAccount account = BankAccount.create(userId, "국민은행", "encrypted", "홍길동");
        ReflectionTestUtils.setField(account, "id", id);
        if (verified) account.verify();
        return account;
    }

    @Nested
    @DisplayName("계좌 등록 + 1원 인증 요청")
    class Register {

        private final BankAccountCreateRequest req =
                new BankAccountCreateRequest("국민은행", "1234567890", "홍길동");

        @Test
        @DisplayName("성공 - 계좌 등록 후 인증 코드 발송")
        void success() {
            given(bankVerificationClient.isBankMaintenanceTime()).willReturn(false);
            given(aesEncryptionUtil.encrypt("1234567890")).willReturn("encrypted");
            given(bankAccountRepository.existsByAccountNumberAndIsVerifiedTrue("encrypted")).willReturn(false);
            given(bankVerificationClient.verifyAccountOwner("국민은행", "1234567890")).willReturn("홍길동");
            given(bankVerificationClient.requestOneWonTransfer("국민은행", "1234567890")).willReturn("5873");

            BankAccount savedAccount = createAccount(1L, USER_ID, false);
            given(bankAccountRepository.save(any())).willReturn(savedAccount);

            AccountVerification savedVerification = AccountVerification.create(1L, "5873");
            given(accountVerificationRepository.save(any())).willReturn(savedVerification);

            BankAccountVerifyResponse res = bankAccountService.registerAndRequestVerification(USER_ID, req);

            assertThat(res.maskedAccountNumber()).isEqualTo("******7890");
            assertThat(res.bankName()).isEqualTo("국민은행");
            assertThat(res.expiredAt()).isNotNull();
            verify(bankAccountRepository).save(any());
            verify(accountVerificationRepository).save(any());
        }

        @Test
        @DisplayName("실패 - 은행 점검시간")
        void failMaintenance() {
            given(bankVerificationClient.isBankMaintenanceTime()).willReturn(true);
            assertThatThrownBy(() -> bankAccountService.registerAndRequestVerification(USER_ID, req))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 이미 인증된 계좌 존재 (타인이 인증한 계좌번호)")
        void failAlreadyVerified() {
            given(bankVerificationClient.isBankMaintenanceTime()).willReturn(false);
            given(aesEncryptionUtil.encrypt("1234567890")).willReturn("encrypted");
            given(bankAccountRepository.existsByAccountNumberAndIsVerifiedTrue("encrypted")).willReturn(true);
            assertThatThrownBy(() -> bankAccountService.registerAndRequestVerification(USER_ID, req))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 중복 계좌번호 (타인이 인증한 계좌번호)")
        void failDuplicate() {
            given(bankVerificationClient.isBankMaintenanceTime()).willReturn(false);
            given(aesEncryptionUtil.encrypt("1234567890")).willReturn("encrypted");
            given(bankAccountRepository.existsByAccountNumberAndIsVerifiedTrue("encrypted")).willReturn(true);
            assertThatThrownBy(() -> bankAccountService.registerAndRequestVerification(USER_ID, req))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 예금주 불일치")
        void failHolderMismatch() {
            given(bankVerificationClient.isBankMaintenanceTime()).willReturn(false);
            given(aesEncryptionUtil.encrypt("1234567890")).willReturn("encrypted");
            given(bankAccountRepository.existsByAccountNumberAndIsVerifiedTrue("encrypted")).willReturn(false);
            given(bankVerificationClient.verifyAccountOwner("국민은행", "1234567890")).willReturn("김철수");
            assertThatThrownBy(() -> bankAccountService.registerAndRequestVerification(USER_ID, req))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    @Nested
    @DisplayName("인증코드 검증")
    class Confirm {

        @Test
        @DisplayName("성공 - 올바른 코드로 인증 완료")
        void success() {
            BankAccount account = createAccount(1L, USER_ID, false);
            AccountVerification verification = AccountVerification.create(1L, "1234");
            given(bankAccountRepository.findById(1L)).willReturn(Optional.of(account));
            given(accountVerificationRepository
                    .findTopByBankAccountIdAndIsVerifiedFalseOrderByCreatedAtDesc(1L))
                    .willReturn(Optional.of(verification));

            VerificationConfirmResponse res = bankAccountService.confirmVerification(
                    USER_ID, new VerificationConfirmRequest(1L, "1234"));

            assertThat(res.isVerified()).isTrue();
            assertThat(account.getIsVerified()).isTrue();
        }

        @Test
        @DisplayName("실패 - 틀린 코드 (남은 횟수 반환)")
        void failWrongCode() {
            BankAccount account = createAccount(1L, USER_ID, false);
            AccountVerification verification = AccountVerification.create(1L, "1234");
            given(bankAccountRepository.findById(1L)).willReturn(Optional.of(account));
            given(accountVerificationRepository
                    .findTopByBankAccountIdAndIsVerifiedFalseOrderByCreatedAtDesc(1L))
                    .willReturn(Optional.of(verification));

            VerificationConfirmResponse res = bankAccountService.confirmVerification(
                    USER_ID, new VerificationConfirmRequest(1L, "9999"));

            assertThat(res.isVerified()).isFalse();
            assertThat(res.remainingAttempts()).isEqualTo(4);
        }

        @Test
        @DisplayName("실패 - 계좌 없음")
        void failAccountNotFound() {
            given(bankAccountRepository.findById(999L)).willReturn(Optional.empty());
            assertThatThrownBy(() -> bankAccountService.confirmVerification(
                    USER_ID, new VerificationConfirmRequest(999L, "1234")))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 다른 유저의 계좌")
        void failNotOwner() {
            BankAccount other = createAccount(1L, 999L, false);
            given(bankAccountRepository.findById(1L)).willReturn(Optional.of(other));
            assertThatThrownBy(() -> bankAccountService.confirmVerification(
                    USER_ID, new VerificationConfirmRequest(1L, "1234")))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 이미 인증 완료된 계좌")
        void failAlreadyVerified() {
            BankAccount account = createAccount(1L, USER_ID, true);
            given(bankAccountRepository.findById(1L)).willReturn(Optional.of(account));
            assertThatThrownBy(() -> bankAccountService.confirmVerification(
                    USER_ID, new VerificationConfirmRequest(1L, "1234")))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 인증 유효시간 만료")
        void failExpired() {
            BankAccount account = createAccount(1L, USER_ID, false);
            AccountVerification verification = AccountVerification.create(1L, "1234");
            ReflectionTestUtils.setField(verification, "expiredAt", LocalDateTime.now().minusMinutes(1));
            given(bankAccountRepository.findById(1L)).willReturn(Optional.of(account));
            given(accountVerificationRepository
                    .findTopByBankAccountIdAndIsVerifiedFalseOrderByCreatedAtDesc(1L))
                    .willReturn(Optional.of(verification));
            assertThatThrownBy(() -> bankAccountService.confirmVerification(
                    USER_ID, new VerificationConfirmRequest(1L, "1234")))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 시도 횟수 초과")
        void failMaxAttempt() {
            BankAccount account = createAccount(1L, USER_ID, false);
            AccountVerification verification = AccountVerification.create(1L, "1234");
            ReflectionTestUtils.setField(verification, "attemptCount", 5);
            given(bankAccountRepository.findById(1L)).willReturn(Optional.of(account));
            given(accountVerificationRepository
                    .findTopByBankAccountIdAndIsVerifiedFalseOrderByCreatedAtDesc(1L))
                    .willReturn(Optional.of(verification));
            assertThatThrownBy(() -> bankAccountService.confirmVerification(
                    USER_ID, new VerificationConfirmRequest(1L, "1234")))
                    .isInstanceOf(ServiceErrorException.class);
        }

        @Test
        @DisplayName("실패 - 인증 건 없음")
        void failVerificationNotFound() {
            BankAccount account = createAccount(1L, USER_ID, false);
            given(bankAccountRepository.findById(1L)).willReturn(Optional.of(account));
            given(accountVerificationRepository
                    .findTopByBankAccountIdAndIsVerifiedFalseOrderByCreatedAtDesc(1L))
                    .willReturn(Optional.empty());
            assertThatThrownBy(() -> bankAccountService.confirmVerification(
                    USER_ID, new VerificationConfirmRequest(1L, "1234")))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }
}