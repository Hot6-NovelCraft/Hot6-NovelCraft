package com.example.hot6novelcraft.domain.episode.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeLikeResponse;
import com.example.hot6novelcraft.domain.episode.service.EpisodeLikeService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/episodes")
@RequiredArgsConstructor
public class EpisodeLikeController {

    private final EpisodeLikeService episodeLikeService;

    /**
     * 회차 좋아요 / 취소
     * 정은식
     */
    @PostMapping("/{episodeId}/like")
    public ResponseEntity<BaseResponse<EpisodeLikeResponse>> toggleLike(
            @PathVariable Long episodeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        EpisodeLikeResponse response =
                episodeLikeService.toggleLike(episodeId, userDetails);

        String message = response.isLiked() ? "좋아요 성공" : "좋아요 취소";

        return ResponseEntity.ok(
                BaseResponse.success("200", message, response)
        );
    }
}