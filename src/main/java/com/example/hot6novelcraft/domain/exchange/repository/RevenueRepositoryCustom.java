package com.example.hot6novelcraft.domain.exchange.repository;

import com.example.hot6novelcraft.domain.exchange.dto.response.RevenueStatisticsResponse.RevenueStatisticsItem;
import com.example.hot6novelcraft.domain.exchange.entity.enums.StatisticsPeriod;

import java.util.List;

public interface RevenueRepositoryCustom {

    List<RevenueStatisticsItem> findStatistics(Long authorId, StatisticsPeriod period, Integer year);
}