package com.example.hot6novelcraft.domain.file.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.file.dto.response.FileUploadResponse;
import com.example.hot6novelcraft.domain.file.service.FileUploadService;
import com.example.hot6novelcraft.domain.mentoring.service.MentorshipService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final MentorshipService mentorshipService;

    /**
     * 채팅 첨부 파일 업로드
     * - 최대 10MB, 이미지/문서만 허용
     * - S3 업로드 후 URL 반환
     */
    @PostMapping("/chat")
    public BaseResponse<FileUploadResponse> uploadChatFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUser().getId();
        log.info("[FileUpload] 파일 업로드 요청 userId={} filename={}", userId, file.getOriginalFilename());

        String fileUrl = fileUploadService.uploadChatFile(file);
        return BaseResponse.success("200", "파일 업로드 완료", new FileUploadResponse(fileUrl));
    }

    /**
     * 멘토링 원고 파일 업로드
     * - txt, hwp 파일만 허용
     * - S3 업로드 후 URL 반환
     * 정은식
     */
    @PostMapping("/manuscripts/upload")
    public ResponseEntity<BaseResponse<FileUploadResponse>> uploadManuscript(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUser().getId();
        log.info("[Manuscript] 원고 업로드 요청 userId={} filename={}", userId, file.getOriginalFilename());

        // MentorshipService 통해서 호출 (권한 검증 포함)
        String fileUrl = mentorshipService.uploadManuscript(file, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "원고 업로드 완료", new FileUploadResponse(fileUrl)));
    }
}
