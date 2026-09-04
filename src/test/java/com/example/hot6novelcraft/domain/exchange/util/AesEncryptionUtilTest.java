package com.example.hot6novelcraft.domain.exchange.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesEncryptionUtilTest {

    private AesEncryptionUtil aesEncryptionUtil;
    private final String SECRET_KEY = "12345678901234567890123456789012"; // 32자 (AES-256용)

    @BeforeEach
    void setUp() {
        aesEncryptionUtil = new AesEncryptionUtil(SECRET_KEY);
    }

    @Test
    @DisplayName("성공 - 암호화된 텍스트를 복호화하면 원본과 일치해야 한다")
    void encryptAndDecryptSuccess() {
        // given
        String plainText = "123-456-7890";

        // when
        String encrypted = aesEncryptionUtil.encrypt(plainText);
        String decrypted = aesEncryptionUtil.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(plainText);
        assertThat(encrypted).isNotEqualTo(plainText); // 암호문은 평문과 달라야 함
    }

    @Test
    @DisplayName("실패 - 잘못된 형식의 암호문을 복호화하면 예외가 발생한다")
    void decryptFail() {
        String invalidText = "this-is-not-base64-encoded";

        assertThatThrownBy(() -> aesEncryptionUtil.decrypt(invalidText))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("복호화에 실패했습니다");
    }
}