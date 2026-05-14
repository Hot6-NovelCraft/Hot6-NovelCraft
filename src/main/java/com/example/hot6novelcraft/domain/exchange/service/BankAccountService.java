package com.example.hot6novelcraft.domain.exchange.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.domain.exchange.client.BankVerificationClient;
import com.example.hot6novelcraft.domain.exchange.dto.request.BankAccountCreateRequest;
import com.example.hot6novelcraft.domain.exchange.dto.request.VerificationConfirmRequest;
import com.example.hot6novelcraft.domain.exchange.dto.response.BankAccountVerifyResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.VerificationConfirmResponse;
import com.example.hot6novelcraft.domain.exchange.entity.AccountVerification;
import com.example.hot6novelcraft.domain.exchange.entity.BankAccount;
import com.example.hot6novelcraft.domain.exchange.exception.ExchangeExceptionEnum;
import com.example.hot6novelcraft.domain.exchange.repository.AccountVerificationRepository;
import com.example.hot6novelcraft.domain.exchange.repository.BankAccountRepository;
import com.example.hot6novelcraft.domain.exchange.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final AccountVerificationRepository accountVerificationRepository;
    private final BankVerificationClient bankVerificationClient;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final RevenueService revenueService;

    /**
     * 계좌 등록 + 1원 인증 요청
     * 1. 은행 점검시간 확인
     * 2. 중복 계좌 확인
     * 3. 예금주 실명 확인 (useB API)
     * 4. BankAccount 생성 (미인증)
     * 5. 1원 입금 요청 (useB API)
     * 6. AccountVerification 생성
     */
    @Transactional
    public BankAccountVerifyResponse registerAndRequestVerification(Long userId, BankAccountCreateRequest request) {
        // 은행 점검시간 확인
        if (bankVerificationClient.isBankMaintenanceTime()) {
            throw new ServiceErrorException(ExchangeExceptionEnum.BANK_API_UNAVAILABLE);
        }

        // 기존 계좌(미인증/인증 모두) 삭제 — 재시도 및 계좌 변경 허용
        bankAccountRepository.deleteAllByUserId(userId);

        // 계좌번호 암호화 — 타인이 이미 인증한 번호인지만 차단
        String encryptedAccountNumber = aesEncryptionUtil.encrypt(request.accountNumber());
        if (bankAccountRepository.existsByAccountNumberAndIsVerifiedTrue(encryptedAccountNumber)) {
            throw new ServiceErrorException(ExchangeExceptionEnum.BANK_ACCOUNT_DUPLICATE);
        }

        // 예금주 실명 확인 (외부 API) — null 반환 시 시뮬레이션 모드이므로 검증 스킵
        String actualHolder = bankVerificationClient.verifyAccountOwner(
                request.bankName(), request.accountNumber()
        );
        if (actualHolder != null && !request.accountHolder().equals(actualHolder)) {
            throw new ServiceErrorException(ExchangeExceptionEnum.BANK_ACCOUNT_HOLDER_MISMATCH);
        }

        // 계좌 등록 (미인증 상태)
        BankAccount bankAccount = BankAccount.create(
                userId,
                request.bankName(),
                encryptedAccountNumber,
                request.accountHolder()
        );
        bankAccountRepository.save(bankAccount);

        // 1원 입금 요청 (외부 API)
        String verificationCode = bankVerificationClient.requestOneWonTransfer(
                request.bankName(), request.accountNumber()
        );

        // 인증 정보 저장
        AccountVerification verification = AccountVerification.create(
                bankAccount.getId(), verificationCode
        );
        accountVerificationRepository.save(verification);

        // 마스킹된 계좌번호로 응답
        String maskedNumber = bankAccount.getMaskedAccountNumber(request.accountNumber());
        return BankAccountVerifyResponse.of(bankAccount, maskedNumber, verification.getExpiredAt());
    }

    /**
     * 인증코드 검증
     * 1. 인증 건 조회 (가장 최근, 미인증)
     * 2. 만료 시간 확인
     * 3. 시도 횟수 확인
     * 4. 코드 비교
     * 5. 성공 시 BankAccount + AccountVerification 인증 처리
     */
    @Transactional
    public VerificationConfirmResponse confirmVerification(Long userId, VerificationConfirmRequest request) {
        // 계좌 소유자 확인
        BankAccount bankAccount = bankAccountRepository.findById(request.bankAccountId())
                .orElseThrow(() -> new ServiceErrorException(ExchangeExceptionEnum.BANK_ACCOUNT_NOT_FOUND));

        if (!bankAccount.getUserId().equals(userId)) {
            throw new ServiceErrorException(ExchangeExceptionEnum.BANK_ACCOUNT_NOT_FOUND);
        }

        if (bankAccount.getIsVerified()) {
            throw new ServiceErrorException(ExchangeExceptionEnum.BANK_ACCOUNT_ALREADY_VERIFIED);
        }

        // 가장 최근 미인증 인증 건 조회
        AccountVerification verification = accountVerificationRepository
                .findTopByBankAccountIdAndIsVerifiedFalseOrderByCreatedAtDesc(request.bankAccountId())
                .orElseThrow(() -> new ServiceErrorException(ExchangeExceptionEnum.VERIFICATION_NOT_FOUND));

        // 만료 시간 확인
        if (verification.isExpired()) {
            throw new ServiceErrorException(ExchangeExceptionEnum.VERIFICATION_EXPIRED);
        }

        // 시도 횟수 확인
        if (verification.isMaxAttemptExceeded()) {
            throw new ServiceErrorException(ExchangeExceptionEnum.VERIFICATION_MAX_ATTEMPT_EXCEEDED);
        }

        // 시도 횟수 증가 (코드 비교 전에 증가시켜야 함)
        verification.increaseAttemptCount();

        // 코드 비교
        if (!verification.getVerificationCode().equals(request.verificationCode())) {
            int remaining = 5 - verification.getAttemptCount();
            log.info("인증 코드 불일치 - bankAccountId: {}, 남은 시도횟수: {}", request.bankAccountId(), remaining);
            return VerificationConfirmResponse.fail(request.bankAccountId(), remaining);
        }

        // 인증 성공 처리
        verification.verify();
        bankAccount.verify();

        revenueService.evictRevenueOverviewCache(userId); // 계좌 인증 후 캐시 무효화

        log.info("계좌 인증 완료 - userId: {}, bankAccountId: {}", userId, bankAccount.getId());
        return VerificationConfirmResponse.success(request.bankAccountId());
    }
}