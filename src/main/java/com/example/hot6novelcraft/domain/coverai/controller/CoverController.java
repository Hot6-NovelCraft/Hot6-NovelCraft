package com.example.hot6novelcraft.domain.coverai.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.coverai.dto.response.CoverJobResponse;
import com.example.hot6novelcraft.domain.coverai.service.CoverService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/novels")
public class CoverController {

    private final CoverService coverService;

    @PostMapping("/{novelId}/cover")
    public ResponseEntity<BaseResponse<CoverJobResponse>> generateCover(
            @PathVariable Long novelId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        CoverJobResponse response = coverService.generateCover(novelId, userDetails.getUser().getId());
        return ResponseEntity.ok(
                BaseResponse.success("200", "소설 표지 생성 요청이 완료되었습니다", response)
        );
    }

    @GetMapping("/cover/status/{jobId}")
    public ResponseEntity<BaseResponse<CoverJobResponse>> getJobStatus(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        CoverJobResponse response = coverService.getJobStatus(jobId, userDetails.getUser().getId());
        return ResponseEntity.ok(
                BaseResponse.success("200", "표지 생성 상태 조회 성공", response)
        );
    }
}