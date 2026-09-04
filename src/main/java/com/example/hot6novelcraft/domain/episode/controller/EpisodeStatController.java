package com.example.hot6novelcraft.domain.episode.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.domain.episode.dto.response.EpisodeStatWithViewResponse;
import com.example.hot6novelcraft.domain.episode.service.EpisodeStatService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class EpisodeStatController {

    private final EpisodeStatService episodeStatService;

    /**
     * 작가용 회차별 통계 조회
     * 정은식
     */
    @GetMapping("/novels/{novelId}/episodes/stats")
    public ResponseEntity<BaseResponse<List<EpisodeStatWithViewResponse>>> getEpisodeStats(
            @PathVariable Long novelId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        List<EpisodeStatWithViewResponse> stats = episodeStatService.getEpisodeStats(novelId, userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "회차 통계 조회 성공", stats));
    }
}