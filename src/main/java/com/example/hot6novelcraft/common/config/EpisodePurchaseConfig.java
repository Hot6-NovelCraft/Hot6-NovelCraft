package com.example.hot6novelcraft.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "episode.bulk-purchase")
@Getter
@Setter
public class EpisodePurchaseConfig {

    private int discountRate = 10;  // 기본값 10%
}