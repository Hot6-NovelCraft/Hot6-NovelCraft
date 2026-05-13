package com.example.hot6novelcraft.domain.mentoring.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.request.MentoringFeedbackRequest;
import com.example.hot6novelcraft.domain.mentoring.dto.response.ManuscriptUrlResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringDetailResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringFeedbackResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringReceivedResponse;
import org.springframework.http.ResponseEntity;
import com.example.hot6novelcraft.domain.mentoring.service.MentoringService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated  // @Min, @Max 동작을 위해 추가
public class MentoringController {

    private final MentoringService mentoringService;

    // =====================================================================
    // 공통 엔드포인트 (V1 / V2 동일 로직)
    // =====================================================================

    @PatchMapping({"/api/v1/mentorings/{mentoringId}/mentees/{menteeId}/accept",
            "/api/v2/mentorings/{mentoringId}/mentees/{menteeId}/accept"})
    public ResponseEntity<BaseResponse<Void>> acceptMentee(
            @PathVariable Long mentoringId,
            @PathVariable Long menteeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        mentoringService.acceptMentee(mentoringId, menteeId, userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "멘티 수락이 완료되었습니다", null));
    }

    @PatchMapping({"/api/v1/mentorings/{mentoringId}/mentees/{menteeId}/reject",
            "/api/v2/mentorings/{mentoringId}/mentees/{menteeId}/reject"})
    public ResponseEntity<BaseResponse<Void>> rejectMentee(
            @PathVariable Long mentoringId,
            @PathVariable Long menteeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        mentoringService.rejectMentee(mentoringId, menteeId, userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "멘티 거절이 완료되었습니다", null));
    }

    @GetMapping({"/api/v1/mentorings/{mentoringId}/documents",
            "/api/v2/mentorings/{mentoringId}/documents"})
    public ResponseEntity<BaseResponse<ManuscriptUrlResponse>> getManuscriptUrl(
            @PathVariable Long mentoringId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        String url = mentoringService.getManuscriptDownloadUrl(mentoringId, userDetails.getUser().getId());
        return ResponseEntity.ok(
                BaseResponse.success("200", "원고 다운로드 URL 조회가 완료되었습니다",
                        new ManuscriptUrlResponse(mentoringId, url))
        );
    }

    @PatchMapping({"/api/v1/mentorings/{mentoringId}/complete",
            "/api/v2/mentorings/{mentoringId}/complete"})
    public ResponseEntity<BaseResponse<Void>> completeMentoring(
            @PathVariable Long mentoringId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        mentoringService.completeMentoring(mentoringId, userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "멘토링이 종료되었습니다", null));
    }

    // =====================================================================
    // getReceivedMentorings — V1 / V2 분리
    // =====================================================================

    // V1: soft-delete 미적용
    @GetMapping("/api/v1/mentorings/received")
    public ResponseEntity<BaseResponse<PageResponse<MentoringReceivedResponse>>> getReceivedMentoringsV1(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "사이즈는 1 이상이어야 합니다")
            @Max(value = 100, message = "사이즈는 100 이하여야 합니다") int size
    ) {
        return ResponseEntity.ok(
                BaseResponse.success("COMMON-200", "접수된 멘토링 목록 조회가 완료되었습니다",
                        PageResponse.register(mentoringService.getReceivedMentorings(
                                userDetails.getUser().getId(), PageRequest.of(page, size))))
        );
    }

    // V2: soft-delete 적용 — 삭제된 소설 제목 노출 방지
    @GetMapping("/api/v2/mentorings/received")
    public ResponseEntity<BaseResponse<PageResponse<MentoringReceivedResponse>>> getReceivedMentoringsV2(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "사이즈는 1 이상이어야 합니다")
            @Max(value = 100, message = "사이즈는 100 이하여야 합니다") int size
    ) {
        return ResponseEntity.ok(
                BaseResponse.success("COMMON-200", "접수된 멘토링 목록 조회가 완료되었습니다",
                        PageResponse.register(mentoringService.getReceivedMentoringsV2(
                                userDetails.getUser().getId(), PageRequest.of(page, size))))
        );
    }

    // =====================================================================
    // getMentoringDetail — V1 / V2 분리
    // =====================================================================

    // V1: soft-delete 미적용
    @GetMapping("/api/v1/mentorings/{mentoringId}")
    public ResponseEntity<BaseResponse<MentoringDetailResponse>> getMentoringDetailV1(
            @PathVariable Long mentoringId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ResponseEntity.ok(
                BaseResponse.success("200", "멘토링 상세 정보 조회가 완료되었습니다",
                        mentoringService.getMentoringDetail(mentoringId, userDetails.getUser().getId()))
        );
    }

    // V2: soft-delete 적용 — 삭제된 소설 제목 노출 방지
    @GetMapping("/api/v2/mentorings/{mentoringId}")
    public ResponseEntity<BaseResponse<MentoringDetailResponse>> getMentoringDetailV2(
            @PathVariable Long mentoringId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ResponseEntity.ok(
                BaseResponse.success("200", "멘토링 상세 정보 조회가 완료되었습니다",
                        mentoringService.getMentoringDetailV2(mentoringId, userDetails.getUser().getId()))
        );
    }

    // =====================================================================
    // createFeedback — V1 / V2 분리
    // =====================================================================

    // V1: 동시성 보호 없음
    @PostMapping("/api/v1/mentorings/{mentoringId}/feedbacks")
    public ResponseEntity<BaseResponse<MentoringFeedbackResponse>> createFeedbackV1(
            @PathVariable Long mentoringId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody MentoringFeedbackRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "피드백이 등록되었습니다",
                        mentoringService.createFeedback(mentoringId, userDetails.getUser().getId(), request)));
    }

    // =====================================================================
    // 멘티 전용 엔드포인트
    // =====================================================================

    // 멘티가 멘토링 종료
    @PatchMapping("/api/v2/mentorings/{mentoringId}/complete-by-mentee")
    public ResponseEntity<BaseResponse<Void>> completeMentoringByMentee(
            @PathVariable Long mentoringId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        mentoringService.completeMentoringByMentee(mentoringId, userDetails.getUser().getId());
        return ResponseEntity.ok(BaseResponse.success("200", "멘토링이 종료되었습니다", null));
    }

    // 멘티가 자신의 멘토링 상세 조회 (피드백 포함)
    @GetMapping("/api/v2/mentorings/{mentoringId}/mentee")
    public ResponseEntity<BaseResponse<MentoringDetailResponse>> getMentoringDetailForMentee(
            @PathVariable Long mentoringId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(BaseResponse.success("200", "멘토링 상세 조회 완료",
                mentoringService.getMentoringDetailForMentee(mentoringId, userDetails.getUser().getId())));
    }

    // V2: 비관적 락 + 유니크 제약으로 sessionNumber 동시성 보호
    @PostMapping("/api/v2/mentorings/{mentoringId}/feedbacks")
    public ResponseEntity<BaseResponse<MentoringFeedbackResponse>> createFeedbackV2(
            @PathVariable Long mentoringId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody MentoringFeedbackRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "피드백이 등록되었습니다",
                        mentoringService.createFeedbackV2(mentoringId, userDetails.getUser().getId(), request)));
    }
}