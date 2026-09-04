package com.example.hot6novelcraft.domain.user.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.domain.user.dto.request.*;
import com.example.hot6novelcraft.domain.user.dto.response.AdminSignupResponse;
import com.example.hot6novelcraft.domain.user.dto.response.CommonSignupResponse;
import com.example.hot6novelcraft.domain.user.dto.response.SignupResponse;
import com.example.hot6novelcraft.domain.user.dto.response.SocialSignupResponse;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.entity.enums.ProviderSns;
import com.example.hot6novelcraft.domain.user.service.SignupService;
import com.example.hot6novelcraft.domain.user.service.SmsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class SignupController {

    private final SignupService signupService;
    private final SmsService smsService;
    private final JwtUtil jwtUtil;

    /**
     * ===== 중복 확인 =====
     * 1. 이메일
     * 2. 닉네임
     */
    @GetMapping("/email/check")
    public ResponseEntity<BaseResponse<Void>> checkEmail(
            @RequestParam String email
    ) {
        signupService.checkEmail(email);

        return ResponseEntity.ok(BaseResponse.success("200", "사용할 수 있는 이메일입니다", null));
    }

    @GetMapping("/nickname/check")
    public ResponseEntity<BaseResponse<Void>> checkNickname(
            @RequestParam String nickname
    ) {
        signupService.checkNickname(nickname);

        return ResponseEntity.ok(BaseResponse.success("200", "사용할 수 있는 닉네임입니다", null));
    }

    /**
     * ===== SMS 인증 =====
     * 1. 인증번호 발송
     * 2. 인증번호 검증
     */
    @PostMapping("/phone/send")
    public ResponseEntity<BaseResponse<Void>> sendVerificationCode(
            @Valid @RequestBody PhoneSendRequest request
    ) {
        smsService.sendSMS(request.phoneNo());
        return ResponseEntity.ok(BaseResponse.success("200", "인증번호를 발송했습니다", null));
    }

    @PostMapping("/phone/verify")
    public ResponseEntity<BaseResponse<String>> phoneVerifyRequest(
            @Valid @RequestBody PhoneVerifyRequest request
    ) {
        String tempToken = smsService.verifyAuthCode(request.phoneNo(), request.verificationCode());
        return ResponseEntity.ok(BaseResponse.success("200", "인증번호가 성공적으로 확인되었습니다", tempToken));
    }

    /**
     * ===== 회원 가입 =====
     * 1. 공통 회원가입 - 일반 이메일 가입
     * 2. 독자 회원가입 - 독자 추가 정보 기입
     * 3. 작가 회원가입 - 작가 추가 정보 기입
     */
    @PostMapping("/signup")
    public ResponseEntity<BaseResponse<CommonSignupResponse>> signup(
            @Valid @RequestBody CommonSignupRequest request
    ) {
        String tempToken = signupService.commonSignup(request);
        return ResponseEntity.ok(BaseResponse.success("200", "입력 정보가 확인되었습니다", CommonSignupResponse.of(tempToken)));
    }

    @PostMapping("/signup/reader")
    public ResponseEntity<BaseResponse<SignupResponse>> readerSignup(
            @Valid @RequestBody ReaderSignupRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        SignupResponse response = signupService.readerSignup(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success("201", "회원가입이 완료되었습니다", response));
    }

    @PostMapping("/signup/author")
    public ResponseEntity<BaseResponse<SignupResponse>> authorSignup(
            @Valid @RequestBody AuthorRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        SignupResponse response = signupService.authorSignup(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success("201", "작가 회원가입이 완료되었습니다", response));
    }

    /**
     * 소셜 로그인
     */
    @PostMapping("/social/signup/{provider}")
    public ResponseEntity<BaseResponse<SocialSignupResponse>> socialSignup(
            @Valid @RequestBody SocialSignupRequest request,
            @PathVariable String provider,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            HttpServletRequest httpRequest
    ) {
        String email = userDetails.getUsername();
        // 소셜 토큰 JWT claims에서 providerId 추출 (OAuth2 attributes 아님)
        String authHeader = httpRequest.getHeader("Authorization");
        String rawToken = authHeader != null ? authHeader.replace("Bearer ", "") : null;
        String providerId = rawToken != null ? jwtUtil.extractProviderId(rawToken) : null;
        ProviderSns providerSns = ProviderSns.from(provider);

        SocialSignupResponse response = signupService.socialCommonSignup(request, email, providerId, providerSns);
        return ResponseEntity.ok(BaseResponse.success("200", "소셜 공통 가입이 완료되었습니다.", response));
    }

    /**
     * 관리자 로그인
     */
    @PostMapping("/signup/admin")
    public ResponseEntity<BaseResponse<AdminSignupResponse>> adminSignup(
            @Valid @RequestBody AdminSignupRequest request
    ) {
        AdminSignupResponse response = signupService.adminSignup(request, request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success("201", "관리자 권한으로 회원가입이 완료되었습니다.", response));
    }
}