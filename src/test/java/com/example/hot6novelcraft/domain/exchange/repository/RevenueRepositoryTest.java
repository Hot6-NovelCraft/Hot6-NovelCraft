package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueStatisticsResponse.RevenueStatisticsItem;
import com.example.hot6novelcraft.domain.exchange.entity.Revenue;
import com.example.hot6novelcraft.domain.exchange.entity.enums.RevenueType;
import com.example.hot6novelcraft.domain.exchange.entity.enums.StatisticsPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import com.example.hot6novelcraft.common.config.QuerydslConfig; // 프로젝트의 QueryDSL 설정 클래스명 확인 필요

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 자동 교체 방지
@Import(QuerydslConfig.class)
class RevenueRepositoryTest {

    @Autowired
    private RevenueRepository revenueRepository;

    @Test
    @DisplayName("성공 - QueryDSL 통계 쿼리가 정확한 금액을 합산하는지 확인한다")
    void findStatistics_Success() {
        // given
        Long authorId = 1L;
        int currentYear = java.time.LocalDate.now().getYear(); // [개선] 하드코딩 제거

        revenueRepository.save(Revenue.create(authorId, 100L, 5000, 5000, RevenueType.EPISODE_SALE));

        // when
        List<RevenueStatisticsItem> result = revenueRepository.findStatistics(authorId, StatisticsPeriod.MONTHLY, currentYear);

        // then
        assertThat(result).isNotEmpty();
    }
}