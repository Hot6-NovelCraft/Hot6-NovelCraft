package com.example.hot6novelcraft.domain.user.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.common.security.RedisUtil;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j(topic = "SmsService")
@Service
public class SmsService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final DefaultMessageService messageService;
    private final RedisUtil redisUtil;
    private final UserRepository userRepository;
    private String fromNumber;
    private final boolean isTestMode;

    public SmsService(
            @Value("${coolsms.api-key}") String apiKey
            , @Value("${coolsms.secret-key}") String apiSecret
            , @Value("${coolsms.send-number}") String fromNumber
            , @Value("${coolsms.test-mode}") boolean isTestMode
            , RedisUtil redisUtil,
            UserRepository userRepository) {
        // 4.x 버전 - 여기서 CoolSMS 객체 초기화
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
        this.fromNumber = fromNumber;
        this.redisUtil = redisUtil;
        this.isTestMode = isTestMode;
        this.userRepository = userRepository;
    }

    private String createRandomCode() {
        StringBuilder randomCode = new StringBuilder(6);

        for (int i = 0; i < 6; i++) {
            randomCode.append(RANDOM.nextInt(10));
        }
        return randomCode.toString();
    }

    // 인증번호 전송
    public void sendSMS(String phoneNumber) {

        String limitKey = "SMS:LIMIT:" + phoneNumber;

        // 24시간(1140) 기준으로 카운트 증가
        Long requestCount = redisUtil.incrementAndExpire(limitKey, 1440);

        if (requestCount != null && requestCount > 5) {
            log.warn("[SMS] 일일 전송 횟수 5회 초과, 수신번호: {}", phoneNumber);
            throw new ServiceErrorException(UserExceptionEnum.ERR_EXCEED_SMS_LIMIT);
        }

        // 랜덤한 인증번호 생성
        String randomCode = createRandomCode();

        // 인증 번호 redis 저장 (TTL 5분)
        String redisKey = "SMS:VERIFIED:" + phoneNumber;
        redisUtil.set(redisKey, randomCode, 5);

        // testMode 켜져있을 때 로그만 찍힘
        if (isTestMode) {
            log.info("[SMS TEST] 진짜 문자를 발송하지 않습니다.");
            log.info("[SMS TEST] 수신번호: {}, 인증번호: [{}]", phoneNumber, randomCode);
            return;
        }

        // 발신 정보 설정
        Message message = new Message();
        message.setFrom(fromNumber);
        message.setTo(phoneNumber);
        message.setText("[NovelCraft] 인증번호: [" + randomCode + "]");

        try {
            SingleMessageSentResponse response = this.messageService.sendOne(
                    new SingleMessageSendingRequest(message)
            );
            log.info("[SMS] 전송 성공, 결과: {}", response);

        } catch (Exception e) {
            log.error("[SMS] 전송 실패", e);
            throw new ServiceErrorException(UserExceptionEnum.ERR_FAILED_SEND_SMS);
        }

    }

    // 인증번호 검증 및 예외 처리
    public String verifyAuthCode(String phoneNumber, String inputCode) {
        String redisKey = "SMS:VERIFIED:" + phoneNumber;

        boolean isVerified = redisUtil.verifyAndDeleteWithLua(redisKey, inputCode);
        if (!isVerified) {
            log.error("[SMS] 인증번호 불일치, key: {}", redisKey);
            throw new ServiceErrorException(UserExceptionEnum.ERR_INVALID_PHONE_VERIFICATION);
        }

        // 검증 성공
        String tempToken = UUID.randomUUID().toString();

        String tokenKey = "SMS:TOKEN:" + tempToken;
        redisUtil.set(tokenKey, phoneNumber, 10);

        log.info("[SMS] 인증번호 검증 성공, key: {}", redisKey);
        return tempToken;
    }

    // SMS + 생일로 성인 인증 완료
    @Transactional
    public void completeAdultVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

        user.verifyAdult();
    }
}
