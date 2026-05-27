package com.example.hot6novelcraft.domain.admin.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.admin.dto.request.AdminMentorRejectRequest;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminPendingMentorResponse;
import com.example.hot6novelcraft.domain.admin.service.AdminMentorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mentors")
@RequiredArgsConstructor
public class AdminMentorController {

    private final AdminMentorService adminMentorService;

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/pending")
    public ResponseEntity<BaseResponse<List<AdminPendingMentorResponse>>> getPendingMentors() {
        List<AdminPendingMentorResponse> responses = adminMentorService.getPendingProficientMentors();

        return ResponseEntity.ok(BaseResponse.success("200", "숙련 (PROFICIENT) 등급 심사 대기 목록 조회 완료", responses));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/{mentorId}/approve")
    public ResponseEntity<BaseResponse<Void>> approveMentor(
            @PathVariable Long mentorId
    ) {
        adminMentorService.approveMentor(mentorId);
        return ResponseEntity.ok(BaseResponse.success("200", "멘토 등급 심사 승급 완료", null));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/{mentorId}/reject")
    public ResponseEntity<BaseResponse<AdminMentorRejectRequest>> rejectMentor(
            @PathVariable Long mentorId,
            @RequestBody AdminMentorRejectRequest request
    ) {
        adminMentorService.rejectMentor(mentorId, request.rejectReason());
        return ResponseEntity.ok(BaseResponse.success("200", "멘토 등급 심사 승급 거절", request));
    }
}