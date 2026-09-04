package com.example.hot6novelcraft.common.config;

import io.portone.sdk.server.payment.PaymentClient;
import io.portone.sdk.server.webhook.WebhookVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortOneConfig {

    @Value("${portone.api-secret}")
    private String apiSecret;

    @Value("${portone.webhook-secret}")
    private String webhookSecret;

    @Bean
    public PaymentClient paymentClient() {
        return new PaymentClient(apiSecret, "https://api.portone.io", null);
    }

    @Bean
    public WebhookVerifier webhookVerifier() {
        return new WebhookVerifier(webhookSecret);
    }
}