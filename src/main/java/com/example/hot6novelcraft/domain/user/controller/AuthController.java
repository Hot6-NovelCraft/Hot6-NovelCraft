package com.example.hot6novelcraft.domain.user.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.common.security.JwtUtil;
import com.example.hot6novelcraft.domain.user.dto.request.*;
import com.example.hot6novelcraft.domain.user.dto.response.*;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.domain.user.service.AuthService;
import com.example.hot6novelcraft.domain.user.service.SmsService;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final SmsService smsService;

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<LoginUserResponse>> login(
            @Valid @RequestBody LoginUserRequest request
    ) {
        LoginUserResponse response = authService.login(request);
        return ResponseEntity.ok(BaseResponse.success("200", "로그인 성공", response));
    }

    @GetMapping("/users/me")
    public ResponseEntity<BaseResponse<MyPageResponse>> getMyPage(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        MyPageResponse response = authService.getMyPage(userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "내 정보 조회 성공", response));
    }

    /**
     * ======== 회원 정보 수정 ========
     * 1. 공통 수정 - 닉네임, 전화번호
     * 2. 작가 프로필 - 장르, 소개글 등
     * 3. 독자 프로필 - 전호 장르, 독서 목표
     * 4. 비번 변경
     * 5. 1년 후 성인 인증 갱신
     */

    @PatchMapping("/users/me")
    public ResponseEntity<BaseResponse<CommonUpdateResponse>> updateUserInfo(
            @Valid @RequestBody CommonUpdateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        CommonUpdateResponse response = authService.updateUserInfo(request, userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "회원정보 수정이 완료되었습니다", response));
    }

    @PatchMapping("/users/me/author")
    public ResponseEntity<BaseResponse<AuthorUpdateResponse>> updateAuthor(
            @Valid @RequestBody AuthorRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        AuthorUpdateResponse response = authService.authorUpdateProfile(request, userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "작가 회원정보 수정이 완료되었습니다.", response));
    }

    @PatchMapping("/users/me/reader")
    public ResponseEntity<BaseResponse<ReaderUpdateResponse>> updateReader(
            @Valid @RequestBody ReaderUpdatedRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        ReaderUpdateResponse response = authService.readerUpdateProfile(request, userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "독자 회원정보 수정이 완료되었습니다.", response));
    }

    @PatchMapping("/users/me/password")
    public ResponseEntity<BaseResponse<Void>> updatePassword(
            @Valid @RequestBody PasswordUpdateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        authService.updatePassword(request.oldPassword(), request.newPassword(), userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "비밀번호가 변경되었습니다", null));
    }

    @PostMapping("/users/me/verify-adult")
    public ResponseEntity<BaseResponse<Void>> reVerifyAdult(
            @RequestBody PhoneVerifyRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        String registeredPhone = userDetails.getUser().getPhoneNo();
        if (!registeredPhone.equals(request.phoneNo())) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_PHONE_NOT_VERIFIED);
        }
        // 프론트에서 받아온 인증번호로 SMS 인증 통과 여부 검증
        smsService.verifyAuthCode(registeredPhone, request.verificationCode());

        // 토큰 갱신 필요 시 새 액세스 토큰 발급 및 응답에 포함
        smsService.completeAdultVerification(userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "성인 인증이 갱신되었습니다", null));
    }

    /**
     * ======== 회원 정보 수정 ========
     * 1. 로그아웃
     * 2. 회원탈퇴
     * 3. 계정복구 (30일 이내)
     * 4. 즉시파기 (새로운 이력생성 위해)
     */
    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(
            @RequestHeader("Authorization") String accessToken,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        authService.logout(accessToken, userDetails.getUser().getEmail());
        return ResponseEntity.ok(BaseResponse.success("200", "로그아웃 성공", null));
    }

    @DeleteMapping("/users/delete")
    public ResponseEntity<BaseResponse<String>> deleteUser(
            @RequestHeader("Authorization") String accessToken,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        authService.withdrawUser(accessToken, userDetails.getUser().getEmail());

        return ResponseEntity.ok(BaseResponse.success("200", "회원탈퇴가 정상적으로 접수되었습니다. 30일 이내 재접속 시, 계정 복구가 가능합니다", null));
    }

    @PatchMapping("/users/restore")
    public ResponseEntity<BaseResponse<String>> restoreUser(
            @Valid @RequestBody UserRestoreRequest request
    ) {
        String verifiedEmail = validateAndGetEmailFromRecoveryToken(request.recoveryToken());
        authService.restoreUser(verifiedEmail);
        return ResponseEntity.ok(BaseResponse.success("200", "계정이 성공적으로 복구되었습니다.", null));
    }

    @PatchMapping("/users/abandon-recovery")
    public ResponseEntity<BaseResponse<Void>> abandonRecovery(
            // (기존에 쓰던 email만 받는 DTO 재사용)
            @Valid @RequestBody UserRestoreRequest request
    ) {
        String verifiedEmail = validateAndGetEmailFromRecoveryToken(request.recoveryToken());
        authService.abandonRecovery(verifiedEmail);
        return ResponseEntity.ok(BaseResponse.success("200", "기존 계정 복구를 포기하고 데이터를 파기했습니다. 신규 가입이 가능합니다.", null));
    }

    // 복구(restore), 즉시 파기(abandon-recovery) 공통 메소드
    private String validateAndGetEmailFromRecoveryToken(String recoveryToken) {
        if (!jwtUtil.validateToken(recoveryToken)) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_INVALID_TOKEN);
        }
        try {
            if (!jwtUtil.isRecoveryToken(recoveryToken)) {
                throw new ServiceErrorException(UserExceptionEnum.ERR_INVALID_TOKEN);
            }
            return jwtUtil.extractEmail(recoveryToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_INVALID_TOKEN);
        }
    }
}
