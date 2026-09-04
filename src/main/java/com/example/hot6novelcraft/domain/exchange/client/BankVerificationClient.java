package com.example.hot6novelcraft.domain.exchange.client;

/**
 * 은행 계좌 인증 API 인터페이스
 * <p>
 * 현재: LocalBankVerificationClient (자체 시뮬레이션)
 * 운영 전환 시: useB, CODEF 등 실제 은행 API 구현체로 교체
 */
public interface BankVerificationClient {

    /**
     * 예금주 실명 확인
     *
     * @param bankName      은행명
     * @param accountNumber 계좌번호 (평문)
     * @return 예금주명
     */
    String verifyAccountOwner(String bankName, String accountNumber);

    /**
     * 1원 입금 요청 + 인증코드 발송
     *
     * @param bankName      은행명
     * @param accountNumber 계좌번호 (평문)
     * @return 인증코드 (입금자명에 포함된 코드)
     */
    String requestOneWonTransfer(String bankName, String accountNumber);

    /**
     * 은행 점검시간 여부 확인
     *
     * @return 점검 중이면 true
     */
    boolean isBankMaintenanceTime();
}