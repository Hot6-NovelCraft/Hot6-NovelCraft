package com.example.hot6novelcraft.domain.exchange.service;

import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueOverviewResponse;
import com.example.hot6novelcraft.domain.exchange.entity.BankAccount;
import com.example.hot6novelcraft.domain.exchange.entity.enums.RevenueType;
import com.example.hot6novelcraft.domain.exchange.repository.BankAccountRepository;
import com.example.hot6novelcraft.domain.exchange.repository.RevenueRepository;
import com.example.hot6novelcraft.domain.exchange.util.AesEncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RevenueServiceTest {

    private RevenueService revenueService;

    @Mock RevenueRepository revenueRepository;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock AesEncryptionUtil aesEncryptionUtil;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOperations;

    private final Long AUTHOR_ID = 1L;

    @BeforeEach
    void setUp() {
        revenueService = new RevenueService(
                revenueRepository, bankAccountRepository, aesEncryptionUtil,
                redisTemplate, new ObjectMapper()
        );
    }

    @Test
    @DisplayName("캐시 미스 - DB 조회 후 캐시 저장")
    void getOverviewCacheMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.EPISODE_SALE)).willReturn(300000);
        given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.SUBSCRIPTION)).willReturn(200000);
        given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.REFUND)).willReturn(0);
        given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.WITHDRAWAL)).willReturn(100000);

        BankAccount account = BankAccount.create(AUTHOR_ID, "국민은행", "encrypted", "홍길동");
        ReflectionTestUtils.setField(account, "id", 1L);
        account.verify();
        given(bankAccountRepository.findByUserIdAndIsVerifiedTrue(AUTHOR_ID)).willReturn(Optional.of(account));
        given(aesEncryptionUtil.decrypt("encrypted")).willReturn("1234567890");

        RevenueOverviewResponse res = revenueService.getRevenueOverview(AUTHOR_ID);

        assertThat(res.totalEarned()).isEqualTo(500000);
        assertThat(res.totalWithdrawn()).isEqualTo(100000);
        assertThat(res.availableBalance()).isEqualTo(400000);
        assertThat(res.bankAccount()).isNotNull();
        assertThat(res.bankAccount().maskedAccountNumber()).isEqualTo("******7890");
        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("캐시 히트 - DB 조회 없이 캐시 반환")
    void getOverviewCacheHit() throws Exception {
        RevenueOverviewResponse cached = RevenueOverviewResponse.of(500000, 100000, 400000, 0, 0, null);
        String cachedJson = new ObjectMapper().writeValueAsString(cached);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(cachedJson);

        RevenueOverviewResponse res = revenueService.getRevenueOverview(AUTHOR_ID);

        assertThat(res.totalEarned()).isEqualTo(500000);
        verify(revenueRepository, never()).sumAmountByAuthorIdAndType(any(), any());
    }

    @Test
    @DisplayName("인증된 계좌 없으면 bankAccount null")
    void getOverviewNoBankAccount() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.EPISODE_SALE)).willReturn(0);
        given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.SUBSCRIPTION)).willReturn(0);
        given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.REFUND)).willReturn(0);
        given(revenueRepository.sumAmountByAuthorIdAndType(AUTHOR_ID, RevenueType.WITHDRAWAL)).willReturn(0);
        given(bankAccountRepository.findByUserIdAndIsVerifiedTrue(AUTHOR_ID)).willReturn(Optional.empty());

        RevenueOverviewResponse res = revenueService.getRevenueOverview(AUTHOR_ID);

        assertThat(res.bankAccount()).isNull();
        assertThat(res.availableBalance()).isEqualTo(0);
    }

    @Test
    @DisplayName("캐시 무효화 - Redis 키 삭제")
    void evictCache() {
        given(redisTemplate.delete(anyString())).willReturn(true);

        revenueService.evictRevenueOverviewCache(AUTHOR_ID);

        verify(redisTemplate).delete(contains("revenue:overview:1"));
    }
}