package com.example.hot6novelcraft.domain.subscription.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionCancelRequest;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionCompleteRequest;
import com.example.hot6novelcraft.domain.subscription.dto.request.SubscriptionPrepareRequest;
import com.example.hot6novelcraft.domain.subscription.dto.response.SubscriptionPrepareResponse;
import com.example.hot6novelcraft.domain.subscription.dto.response.SubscriptionResponse;
import com.example.hot6novelcraft.domain.subscription.service.SubscriptionService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * 구독 준비 - subscriptionKey 발급
     */
    @PostMapping("/prepare")
    public ResponseEntity<BaseResponse<SubscriptionPrepareResponse>> prepareSubscription(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody SubscriptionPrepareRequest request
    ) {
        Long userId = userDetails.getUser().getId();

        SubscriptionPrepareResponse response = subscriptionService.prepareSubscription(userId, request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(HttpStatus.OK.name(), "구독 준비가 완료되었습니다", response));
    }

    /**
     * 구독 완료 - 빌링키로 첫 결제 + 구독 활성화
     */
    @PostMapping("/complete")
    public ResponseEntity<BaseResponse<SubscriptionResponse>> completeSubscription(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody SubscriptionCompleteRequest request
    ) {
        Long userId = userDetails.getUser().getId();

        SubscriptionResponse response = subscriptionService.completeSubscription(userId, request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(HttpStatus.OK.name(), "구독이 완료되었습니다", response));
    }

    /**
     * 내 구독 조회
     */
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<SubscriptionResponse>> getMySubscription(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Long userId = userDetails.getUser().getId();

        SubscriptionResponse response = subscriptionService.getMySubscription(userId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(HttpStatus.OK.name(), "구독 조회 성공", response));
    }

    /**
     * 구독 취소
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> cancelSubscription(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) SubscriptionCancelRequest request
    ) {
        Long userId = userDetails.getUser().getId();

        subscriptionService.cancelSubscription(userId, id, request != null ? request : new SubscriptionCancelRequest(null));

        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(HttpStatus.OK.name(), "구독이 취소되었습니다", null));
    }
}
