package com.example.hot6novelcraft.domain.aichat.controller;

import com.example.hot6novelcraft.domain.aichat.dto.AiChatRequest;
import com.example.hot6novelcraft.domain.aichat.service.AiChatService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai-support")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * SSE 스트리밍 응답으로 AI 고객센터에 질문합니다.
     * produces = text/event-stream → 브라우저/클라이언트가 SSE로 수신
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody AiChatRequest request
    ) {
        Long userId = userDetails.getUser().getId();
        return aiChatService.chat(userId, request.message());
    }

    /**
     * 대화 세션을 초기화합니다. (2단계에서 실제 동작)
     */
    @DeleteMapping("/chat/session")
    public ResponseEntity<Void> clearSession(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Long userId = userDetails.getUser().getId();
        aiChatService.clearSession(userId);
        return ResponseEntity.noContent().build();
    }
}
