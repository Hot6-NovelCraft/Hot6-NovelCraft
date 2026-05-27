package com.example.hot6novelcraft.domain.exchange.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1. public 키워드를 추가하여 Gradle 스캐너가 인식하게 합니다.
 * 2. @ActiveProfiles를 통해 LocalBankVerificationClient의 @Profile({"test"})이 활성화되도록 보장합니다.
 */
@ActiveProfiles("test")
public class LocalBankVerificationClientTest { // public 추가

    private LocalBankVerificationClient client;

    @BeforeEach
    void setUp() {
        client = new LocalBankVerificationClient();
    }

    @Test
    @DisplayName("성공 - 기등록된 가상 계좌의 예금주를 확인한다")
    void verifyAccountOwner_Success() {
        String holder = client.verifyAccountOwner("국민은행", "1234567890");
        assertThat(holder).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("성공 - 동적으로 등록한 계좌의 예금주를 확인한다")
    void verifyDynamicAccount_Success() {
        client.registerVirtualAccount("농협은행", "111222333", "전민우");

        String holder = client.verifyAccountOwner("농협은행", "111222333");
        assertThat(holder).isEqualTo("전민우");
    }

    @Test
    @DisplayName("실패 - 존재하지 않는 계좌 요청 시 null을 반환한다")
    void verifyAccountOwner_Fail() {
        String result = client.verifyAccountOwner("신한은행", "0000000000");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("성공 - 1원 입금 요청 시 4자리 숫자를 반환한다")
    void requestOneWonTransfer_Success() {
        String code = client.requestOneWonTransfer("국민은행", "1234567890");

        assertThat(code).hasSize(4);
        assertThat(code.matches("\\d{4}")).isTrue();
    }

    @Test
    @DisplayName("성공 - 은행 점검 시간 여부를 확인한다")
    void isBankMaintenanceTime() {
        client.isBankMaintenanceTime();
    }
}