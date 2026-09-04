package com.example.hot6novelcraft.domain.episode.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.episode.dto.request.EpisodeCommentCreateRequest;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeCommentCreateResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeCommentListResponse;
import com.example.hot6novelcraft.domain.episode.service.EpisodeCommentService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EpisodeCommentController {

    private final EpisodeCommentService episodeCommentService;

    /**
     * 댓글 작성
     * 정은식
     */
    @PostMapping("episodes/{episodeId}/comments")
    public ResponseEntity<BaseResponse<EpisodeCommentCreateResponse>> createComment(
            @PathVariable Long episodeId,
            @Valid @RequestBody EpisodeCommentCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        EpisodeCommentCreateResponse response =
                episodeCommentService.createComment(episodeId, request, userDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "댓글 작성 성공", response));
    }

    /**
     * 댓글 삭제 (hard delete)
     * 정은식
     */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<BaseResponse<Void>> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        episodeCommentService.deleteComment(commentId, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "댓글 삭제 성공", null)
        );
    }

    /**
     * 댓글 목록 조회
     * 정은식
     */
    @GetMapping("/episodes/{episodeId}/comments")
    public ResponseEntity<BaseResponse<PageResponse<EpisodeCommentListResponse>>> getCommentList(
            @PathVariable Long episodeId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<EpisodeCommentListResponse> response =
                episodeCommentService.getCommentList(episodeId, pageable);

        return ResponseEntity.ok(
                BaseResponse.success("200", "댓글 목록 조회 성공", response)
        );
    }
}