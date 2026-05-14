package com.example.hot6novelcraft.domain.exchange.service;

import com.example.hot6novelcraft.domain.exchange.dto.response.BankAccountInfoResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueOverviewResponse;
import com.example.hot6novelcraft.domain.exchange.entity.BankAccount;
import com.example.hot6novelcraft.domain.exchange.entity.enums.RevenueType;
import com.example.hot6novelcraft.domain.exchange.repository.BankAccountRepository;
import com.example.hot6novelcraft.domain.exchange.repository.RevenueRepository;
import com.example.hot6novelcraft.domain.exchange.util.AesEncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevenueService {

    private static final String REVENUE_OVERVIEW_KEY_PREFIX = "revenue:overview:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final RevenueRepository revenueRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 수익 현황 조회
     * - 총 누적 수익 (EPISODE_SALE + SUBSCRIPTION)
     * - 총 환전 금액 (WITHDRAWAL)
     * - 현재 가용 잔액 (누적 수익 - 환전 금액)
     * - 인증된 계좌 정보
     */
    public RevenueOverviewResponse getRevenueOverview(Long authorId) {
        String cacheKey = REVENUE_OVERVIEW_KEY_PREFIX + authorId;

        // Redis 캐시 조회 (JSON 문자열로 저장/역직렬화)
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof String jsonStr) {
                log.debug("수익 현황 캐시 히트 - authorId: {}", authorId);
                return objectMapper.readValue(jsonStr, RevenueOverviewResponse.class);
            }
        } catch (Exception e) {
            log.warn("수익 현황 캐시 역직렬화 실패, DB 조회로 전환 - authorId: {}", authorId);
        }

        // 수익 유형별 합산
        Integer episodeSaleAmount = revenueRepository.sumAmountByAuthorIdAndType(
                authorId, RevenueType.EPISODE_SALE
        );
        Integer subscriptionAmount = revenueRepository.sumAmountByAuthorIdAndType(
                authorId, RevenueType.SUBSCRIPTION
        );
        Integer refundAmount = revenueRepository.sumAmountByAuthorIdAndType(
                authorId, RevenueType.REFUND
        );

        // 총 누적 수익
        Integer totalEarned = episodeSaleAmount + subscriptionAmount + refundAmount;

        // 총 환전 금액
        Integer totalWithdrawn = revenueRepository.sumAmountByAuthorIdAndType(
                authorId, RevenueType.WITHDRAWAL
        );

        // 가용 잔액
        Integer availableBalance = totalEarned - totalWithdrawn;

        // 인증된 계좌 정보
        BankAccountInfoResponse bankAccountInfo = bankAccountRepository
                .findByUserIdAndIsVerifiedTrue(authorId)
                .map(this::toBankAccountInfo)
                .orElse(null);

        RevenueOverviewResponse response = RevenueOverviewResponse.of(
                totalEarned, totalWithdrawn, availableBalance,
                episodeSaleAmount, subscriptionAmount, bankAccountInfo
        );

        // Redis 캐시 저장 (JSON 문자열로 직렬화하여 저장 - Record 클래스 타입 정보 보존)
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
            log.debug("수익 현황 캐시 저장 - authorId: {}", authorId);
        } catch (Exception e) {
            log.warn("수익 현황 캐시 저장 실패 - authorId: {}", authorId);
        }

        return response;
    }

    /**
     * 수익 현황 캐시 무효화
     * - 환전 신청/승인/거절, 수익 발생 시 호출
     */
    public void evictRevenueOverviewCache(Long authorId) {
        String cacheKey = REVENUE_OVERVIEW_KEY_PREFIX + authorId;
        redisTemplate.delete(cacheKey);
        log.debug("수익 현황 캐시 무효화 - authorId: {}", authorId);
    }

    private BankAccountInfoResponse toBankAccountInfo(BankAccount bankAccount) {
        String decryptedNumber = aesEncryptionUtil.decrypt(bankAccount.getAccountNumber());
        String maskedNumber = bankAccount.getMaskedAccountNumber(decryptedNumber);

        return BankAccountInfoResponse.of(
                bankAccount.getId(),
                bankAccount.getBankName(),
                maskedNumber,
                bankAccount.getAccountHolder(),
                bankAccount.getIsVerified()
        );
    }
}