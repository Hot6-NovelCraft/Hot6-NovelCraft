package com.example.hot6novelcraft.domain.admin.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminResponse;
import com.example.hot6novelcraft.domain.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 관리자 승인 대기 목록 조회
     */
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @GetMapping("/pending")
    public ResponseEntity<BaseResponse<List<AdminResponse>>> getPendingAdmins() {
        List<AdminResponse> responses = adminUserService.getPendingAdmins();
        return ResponseEntity.ok(BaseResponse.success("200", "관리자 승인 대기 목록 조회 완료", responses));
    }

    /**
     * 관리자 승인 완료
     */
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PatchMapping("/{userId}/approve")
    public ResponseEntity<BaseResponse<Void>> approveUser(
            @PathVariable Long userId
    ) {
        adminUserService.approvePendingAdmin(userId);
        return ResponseEntity.ok(BaseResponse.success("200", "일반 관리자 승인 완료", null));
    }

    /**
     * 관리자 승인 거절 (계정 삭제)
     */

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PatchMapping("/{userId}/reject")
    public ResponseEntity<BaseResponse<Void>> rejectUser(
            @PathVariable Long userId
    ) {
        adminUserService.rejectPendingAdmin(userId);
        return ResponseEntity.ok(BaseResponse.success("200", "관리자 가입 거절 완료", null));
    }
}
