package com.example.hot6novelcraft.domain.payment.controller;

import com.example.hot6novelcraft.domain.payment.dto.request.WebhookRequest;
import com.example.hot6novelcraft.domain.payment.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 포트원 웹훅 수신 컨트롤러
 *
 * 포트원은 결제 상태 변경 시 이 엔드포인트로 POST 요청을 보낸다.
 * 포트원은 200 OK를 받지 못하면 재시도하므로 항상 200을 반환한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final WebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    @PostMapping("/portone")
    public ResponseEntity<Void> handlePortOneWebhook(
            @RequestHeader(value = WebhookVerifier.HEADER_ID, required = false) String webhookId,
            @RequestHeader(value = WebhookVerifier.HEADER_SIGNATURE, required = false) String webhookSignature,
            @RequestHeader(value = WebhookVerifier.HEADER_TIMESTAMP, required = false) String webhookTimestamp,
            @RequestBody String rawBody
    ) {
        try {
            webhookVerifier.verify(rawBody, webhookId, webhookSignature, webhookTimestamp);
            WebhookRequest request = objectMapper.readValue(rawBody, WebhookRequest.class);
            webhookService.handleWebhook(request);
        } catch (WebhookVerificationException e) {
            log.warn("웹훅 서명 검증 실패 webhookId={}", webhookId, e);
        } catch (Exception e) {
            log.error("웹훅 처리 실패 webhookId={}", webhookId, e);
        }
        return ResponseEntity.ok().build();
    }
}
