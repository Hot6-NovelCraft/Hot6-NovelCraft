package com.example.hot6novelcraft.domain.exchange.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int NONCE_LENGTH = 12;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesEncryptionUtil(
            @Value("${encryption.aes.secret-key}") String key
    ) {
        this.secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
    }

    /**
     * 암호화: 랜덤 nonce 생성 → GCM 암호화 → [nonce + ciphertext] Base64 인코딩
     * 동일 평문이라도 매번 다른 암호문이 생성됨 (nonce가 랜덤)
     */
    public String encrypt(String plainText) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // nonce + ciphertext 결합
            ByteBuffer buffer = ByteBuffer.allocate(nonce.length + encrypted.length);
            buffer.put(nonce);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("계좌번호 암호화에 실패했습니다", e);
        }
    }

    /**
     * 복호화: Base64 디코딩 → nonce 분리 → GCM 복호화
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] nonce = new byte[NONCE_LENGTH];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("계좌번호 복호화에 실패했습니다", e);
        }
    }
}