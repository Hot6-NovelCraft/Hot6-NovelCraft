package com.example.hot6novelcraft.domain.webhookevent.repository;

import com.example.hot6novelcraft.domain.webhookevent.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByWebhookId(String webhookId);
}