package com.example.hot6novelcraft.domain.mentor.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorRegisterRequest;
import com.example.hot6novelcraft.domain.mentor.dto.request.MentorUpdateRequest;
import com.example.hot6novelcraft.domain.mentor.dto.response.*;
import com.example.hot6novelcraft.domain.mentor.service.MentorService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mentors")
@RequiredArgsConstructor
public class MentorController {

    private final MentorService mentorService;

    // 멘토 등록 신청
    @PostMapping
    public ResponseEntity<BaseResponse<MentorRegisterResponse>> register(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody MentorRegisterRequest request
    ) {
        MentorRegisterResponse response = mentorService.register(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "멘토 신청이 정상적으로 접수되었습니다", response));
    }

    // 멘토 정보 수정
    @PutMapping("/me")
    public ResponseEntity<BaseResponse<MentorUpdateResponse>> update(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody MentorUpdateRequest request
    ) {
        MentorUpdateResponse response = mentorService.update(userDetails.getUser().getId(), request);
        return ResponseEntity.ok(BaseResponse.success("200", "멘토 정보가 수정되었습니다", response));
    }

    // 내 멘토 프로필 조회
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<MentorProfileResponse>> getMyProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        MentorProfileResponse response = mentorService.getMyProfile(userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "내 멘토 프로필 조회 성공", response));
    }

    // 내 멘토 등록 상태 조회
    @GetMapping("/me/status")
    public ResponseEntity<BaseResponse<MentorStatusResponse>> getMyStatus(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        MentorStatusResponse response = mentorService.getMyStatus(userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "멘토 등록 상태 조회가 완료되었습니다", response));
    }

    // 내 멘토링 통계 조회
    @GetMapping("/me/statistics")
    public ResponseEntity<BaseResponse<MentorStatisticsResponse>> getStatistics(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        MentorStatisticsResponse response = mentorService.getStatistics(userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "멘토링 통계 조회가 완료되었습니다", response));
    }

    @GetMapping("/me/mentees")
    public ResponseEntity<BaseResponse<List<MenteeInfoResponse>>> getMyMentees(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        List<MenteeInfoResponse> response = mentorService.getMyMentees(userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "내 멘티 목록 조회가 완료되었습니다", response));
    }

    // 내 멘티 목록 조회 v2 - QueryDSL N+1 개선
    @GetMapping("/v2/me/mentees")
    public ResponseEntity<BaseResponse<List<MenteeInfoResponse>>> getMyMenteesV2(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        List<MenteeInfoResponse> response = mentorService.getMyMenteesV2(userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "내 멘티 목록 조회가 완료되었습니다", response));
    }

    @GetMapping("/me/statistics/detail")
    public ResponseEntity<BaseResponse<MentorStatisticsDetailResponse>> getStatisticsDetail(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        MentorStatisticsDetailResponse response = mentorService.getStatisticsDetail(userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "멘토링 통계 상세 조회가 완료되었습니다", response));
    }
}
