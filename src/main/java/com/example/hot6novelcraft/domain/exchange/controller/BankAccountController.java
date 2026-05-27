package com.example.hot6novelcraft.domain.exchange.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.exchange.dto.request.BankAccountCreateRequest;
import com.example.hot6novelcraft.domain.exchange.dto.request.VerificationConfirmRequest;
import com.example.hot6novelcraft.domain.exchange.dto.response.BankAccountVerifyResponse;
import com.example.hot6novelcraft.domain.exchange.dto.response.VerificationConfirmResponse;
import com.example.hot6novelcraft.domain.exchange.service.BankAccountService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/revenues/me/account")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    /**
     * 계좌 등록 + 1원 인증 요청
     */
    @PostMapping("/verify")
    public ResponseEntity<BaseResponse<BankAccountVerifyResponse>> requestVerification(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody BankAccountCreateRequest request
    ) {
        Long userId = userDetails.getUser().getId();
        BankAccountVerifyResponse response = bankAccountService.registerAndRequestVerification(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("CREATED", "인증 코드가 발송되었습니다", response));
    }

    /**
     * 인증코드 검증
     */
    @PostMapping("/verify/confirm")
    public ResponseEntity<BaseResponse<VerificationConfirmResponse>> confirmVerification(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody VerificationConfirmRequest request
    ) {
        Long userId = userDetails.getUser().getId();
        VerificationConfirmResponse response = bankAccountService.confirmVerification(userId, request);

        if (response.isVerified()) {
            return ResponseEntity.ok(
                    BaseResponse.success("OK", "계좌 인증이 완료되었습니다", response)
            );
        }

        return ResponseEntity.ok(
                BaseResponse.success("OK",
                        String.format("인증 코드가 일치하지 않습니다 (남은 시도횟수: %d회)", response.remainingAttempts()),
                        response)
        );
    }
}