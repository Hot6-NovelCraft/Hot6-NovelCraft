package com.example.hot6novelcraft.domain.novel.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelBookmarkResponse;
import com.example.hot6novelcraft.domain.novel.service.NovelBookmarkService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/novels")
@RequiredArgsConstructor
public class NovelBookmarkController {

    private final NovelBookmarkService novelBookmarkService;

    /**
     * 소설 찜 / 취소
     */
    @PostMapping("/{novelId}/bookmark")
    public ResponseEntity<BaseResponse<NovelBookmarkResponse>> toggleBookmark(
            @PathVariable Long novelId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        NovelBookmarkResponse response =
                novelBookmarkService.toggleBookmark(novelId, userDetails);

        String message = response.isBookmarked() ? "찜 성공" : "찜 취소";

        return ResponseEntity.ok(
                BaseResponse.success("OK", message, response)
        );
    }
}