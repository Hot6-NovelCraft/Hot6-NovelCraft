package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.common.config.QuerydslConfig;
import com.example.hot6novelcraft.domain.exchange.entity.Withdrawal;
import com.example.hot6novelcraft.domain.exchange.entity.enums.WithdrawalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) //
public class WithdrawalRepositoryTest {

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Test
    @DisplayName("성공 - 모든 필터가 null일 때도 정상 조회되어야 함 (분기점 커버리지)")
    void findWithFilters_NullFilters_Success() {
        // given
        Long authorId = 1L;
        withdrawalRepository.save(Withdrawal.request(authorId, 100L, 5000, 500));

        // when: 모든 필터를 null로 전달하여 Impl 내부의 삼항 연산자 null 반환 케이스 통과
        Page<Withdrawal> result = withdrawalRepository.findWithFilters(
                authorId, null, null, null, PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("성공 - 모든 필터가 존재할 때 정상 조회되어야 함 (로직 커버리지)")
    void findWithFilters_FullFilters_Success() {
        // given
        Long authorId = 1L;
        withdrawalRepository.save(Withdrawal.request(authorId, 100L, 5000, 500));

        LocalDateTime now = LocalDateTime.now();

        // when: 모든 필터에 값을 넣어 Impl 내부의 eq, goe, loe 로직 모두 실행
        Page<Withdrawal> result = withdrawalRepository.findWithFilters(
                authorId,
                WithdrawalStatus.PENDING,
                now.minusDays(1),
                now.plusDays(1),
                PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(WithdrawalStatus.PENDING);
    }
}