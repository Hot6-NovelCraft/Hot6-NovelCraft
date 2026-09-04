package com.example.hot6novelcraft.domain.payment.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentCancelRequest;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.hot6novelcraft.domain.payment.dto.request.PaymentPrepareRequest;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentHistoryResponse;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentPrepareResponse;
import com.example.hot6novelcraft.domain.payment.dto.response.PaymentResponse;
import com.example.hot6novelcraft.domain.payment.service.PaymentService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 내 결제 내역 목록 조회 (최신순, 페이징)
     * GET /api/payments?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<PaymentHistoryResponse>>> getPaymentHistory(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        Long userId = userDetails.getUser().getId();
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<PaymentHistoryResponse> response = paymentService.getPaymentHistory(userId, pageable);
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(HttpStatus.OK.name(), "결제 내역 조회 성공", response));
    }

    /**
     * 결제 준비 — 결제창 열기 전 PENDING Payment 미리 생성
     * POST /api/payments/prepare
     *
     * 서버가 paymentKey를 생성하여 반환. 프론트는 이 paymentKey로 PortOne SDK를 열고,
     * 결제 완료 후 /confirm에도 동일한 paymentKey를 사용한다.
     * 결제창 이용 중 토큰이 만료되어도 웹훅으로 포인트 충전이 보장된다.
     */
    @PostMapping("/prepare")
    public ResponseEntity<BaseResponse<PaymentPrepareResponse>> preparePayment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody @Valid PaymentPrepareRequest request
    ) {
        Long userId = userDetails.getUser().getId();
        PaymentPrepareResponse response = paymentService.preparePayment(userId, request.amount());
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(HttpStatus.OK.name(), "결제 준비가 완료되었습니다", response));
    }

    /**
     * 결제 확인 및 포인트 충전
     * POST /api/payments/confirm
     *
     * 프론트가 포트원 SDK로 결제 완료 후 /prepare에서 받은 paymentKey와 amount를 전송한다.
     * 서버는 포트원 API로 금액·상태를 검증한 뒤 포인트를 충전한다.
     */
    @PostMapping("/confirm")
    public ResponseEntity<BaseResponse<PaymentResponse>> confirmPayment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody @Valid PaymentConfirmRequest request
    ) {
        Long userId = userDetails.getUser().getId();
        PaymentResponse response = paymentService.confirmPayment(userId, request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(HttpStatus.OK.name(), "결제가 완료되었습니다", response));
    }

    /**
     * 전액 환불
     * POST /api/payments/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<BaseResponse<PaymentResponse>> cancelPayment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long paymentId,
            @RequestBody @Valid PaymentCancelRequest request
    ) {
        Long userId = userDetails.getUser().getId();
        PaymentResponse response = paymentService.cancelPayment(userId, paymentId, request.reason());
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(HttpStatus.OK.name(), "환불이 완료되었습니다", response));
    }
}
