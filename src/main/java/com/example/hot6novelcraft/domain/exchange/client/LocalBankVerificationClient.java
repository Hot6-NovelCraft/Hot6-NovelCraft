package com.example.hot6novelcraft.domain.exchange.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1원 계좌 인증 시뮬레이션 구현체
 * <p>
 * 금융규제로 인해 사업자 등록 없이는 실제 은행 API(useB, CODEF 등) 연동이 불가능하므로,
 * 실제 인증 플로우와 동일한 비즈니스 로직을 자체적으로 시뮬레이션합니다.
 * <p>
 * 운영 환경에서는 이 클래스 대신 BankVerificationClient 인터페이스를 구현한
 * 실제 API 연동 구현체(예: UseBVerificationClient)로 교체하면 됩니다.
 */
@Slf4j
@Component
@Profile({"dev", "local", "test"})
public class LocalBankVerificationClient implements BankVerificationClient {

    private static final Random RANDOM = new Random();

    private static final Map<String, String> VIRTUAL_ACCOUNTS = Map.of(
            "국민은행:1234567890", "홍길동",
            "신한은행:9876543210", "김철수",
            "우리은행:1111222233", "이영희",
            "하나은행:4444555566", "박민수",
            "카카오뱅크:3333444455", "최지은"
    );

    private final Map<String, String> dynamicAccounts = new ConcurrentHashMap<>();

    @Override
    public String verifyAccountOwner(String bankName, String accountNumber) {
        log.debug("[Local] 예금주 확인 요청 - 은행: {}, 계좌: {}", bankName, maskAccount(accountNumber));

        String key = bankName + ":" + accountNumber;

        String holder = VIRTUAL_ACCOUNTS.get(key);
        if (holder == null) {
            holder = dynamicAccounts.get(key);
        }

        if (holder == null) {
            log.debug("[Local] 등록되지 않은 계좌 - 시뮬레이션 모드로 예금주 확인 통과");
            return null; // null 반환 시 서비스에서 예금주 검증 스킵
        }

        log.debug("[Local] 예금주 확인 완료");
        return holder;
    }

    @Override
    public String requestOneWonTransfer(String bankName, String accountNumber) {
        String code = String.format("%04d", RANDOM.nextInt(10000));

        log.debug("[Local] 1원 입금 시뮬레이션 - 은행: {}, 계좌: {}", bankName, maskAccount(accountNumber));
        log.info("[Local] 테스트용 인증코드: {}", code);

        return code;
    }

    @Override
    public boolean isBankMaintenanceTime() {
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.of(23, 30);
        LocalTime end = LocalTime.of(0, 30);

        // [개선] !now.isBefore(start) 는 23:30:00을 포함 (이상/이하 처리)
        boolean isMaintenance = !now.isBefore(start) || !now.isAfter(end);

        if (isMaintenance) {
            log.info("[Local] 은행 점검시간 - 현재시간: {}", now);
        }

        return isMaintenance;
    }

    public void registerVirtualAccount(String bankName, String accountNumber, String accountHolder) {
        String key = bankName + ":" + accountNumber;
        dynamicAccounts.put(key, accountHolder);
        log.debug("[Local] 가상 계좌 등록 완료");
    }

    private String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "*".repeat(accountNumber.length() - 4)
                + accountNumber.substring(accountNumber.length() - 4);
    }
}