package com.example.hot6novelcraft.domain.episode.dto.response;

import com.example.hot6novelcraft.domain.episode.entity.Episode;

import java.util.List;

public record NovelBulkPurchaseResponse(
        Long novelId,
        int totalEpisodes,
        int originalPrice,
        int discountRate,
        int discountAmount,
        int finalPrice,
        Long remainingBalance,
        List<Long> purchasedEpisodeIds
) {
    public static NovelBulkPurchaseResponse of(
            Long novelId,
            List<Episode> purchasedEpisodes,
            int discountRate,
            Long remainingBalance
    ) {
        int originalPrice = purchasedEpisodes.stream()
                .mapToInt(Episode::getPointPrice)
                .sum();
        int discountAmount = originalPrice * discountRate / 100;
        int finalPrice = originalPrice - discountAmount;

        return new NovelBulkPurchaseResponse(
                novelId,
                purchasedEpisodes.size(),
                originalPrice,
                discountRate,
                discountAmount,
                finalPrice,
                remainingBalance,
                purchasedEpisodes.stream().map(Episode::getId).toList()
        );
    }
}