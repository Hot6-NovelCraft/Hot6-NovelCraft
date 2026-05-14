package com.example.hot6novelcraft.domain.episode.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.episode.dto.request.EpisodeCreateRequest;
import com.example.hot6novelcraft.domain.episode.dto.request.EpisodeUpdateRequest;
import com.example.hot6novelcraft.domain.episode.dto.response.*;
import com.example.hot6novelcraft.domain.episode.service.EpisodeService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api")
public class EpisodeController {

    private final EpisodeService episodeService;

    /**
     * 회차 생성
     * 정은식
     */
    @PostMapping("/novels/{novelId}/episodes")
    public ResponseEntity<BaseResponse<EpisodeCreateResponse>> createEpisode(
            @PathVariable Long novelId,
            @Valid @RequestBody EpisodeCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        EpisodeCreateResponse response = episodeService.createEpisode(novelId, request, userDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "회차 생성 성공", response));
    }

    /**
     * 회차 수정
     * 정은식
     */
    @PatchMapping("/episodes/{episodeId}")
    public ResponseEntity<BaseResponse<EpisodeUpdateResponse>> updateEpisode(
            @PathVariable Long episodeId,
            @Valid @RequestBody EpisodeUpdateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        EpisodeUpdateResponse response = episodeService.updateEpisode(episodeId, request, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "회차 수정 성공", response)
        );
    }

    /**
     * 회차 삭제
     * 정은식
     */
    @DeleteMapping("/episodes/{episodeId}")
    public ResponseEntity<BaseResponse<EpisodeDeleteResponse>> deleteEpisode(
            @PathVariable Long episodeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        EpisodeDeleteResponse response = episodeService.deleteEpisode(episodeId, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "회차 삭제 성공", response)
        );
    }

    /**
     * 회차 발행
     * 정은식
     */
    @PostMapping("/episodes/{episodeId}/publish")
    public ResponseEntity<BaseResponse<EpisodePublishResponse>> publishEpisode(
            @PathVariable Long episodeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        EpisodePublishResponse response = episodeService.publishEpisode(episodeId, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "회차 발행 성공", response)
        );
    }

    /**
     * 회차 목록 조회
     * 정은식
     */
    @GetMapping("/novels/{novelId}/episodes")
    public ResponseEntity<BaseResponse<PageResponse<EpisodeListResponse>>> getEpisodeList(
            @PathVariable Long novelId,
            @PageableDefault(size = 20, sort = "episodeNumber", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<EpisodeListResponse> response = episodeService.getEpisodeList(novelId, pageable);

        return ResponseEntity.ok(
                BaseResponse.success("200", "회차 목록 조회 성공", response)
        );
    }

    /**
     * 회차 본문 조회 V1 (JPA)
     * 정은식
     */
    @GetMapping("/v1/episodes/{episodeId}")
    public ResponseEntity<BaseResponse<EpisodeDetailResponse>> getEpisodeContentV1(
            @PathVariable Long episodeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        EpisodeDetailResponse response = episodeService.getEpisodeContentV1(episodeId, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "회차 본문 조회 성공(V1)", response)
        );
    }

    /**
     * 회차 본문 조회 V2 (Hot Key + 캐싱)
     * 정은식
     */
    @GetMapping("/v2/episodes/{episodeId}")
    public ResponseEntity<BaseResponse<EpisodeDetailResponse>> getEpisodeContentV2(
            @PathVariable Long episodeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        EpisodeDetailResponse response = episodeService.getEpisodeContentV2(episodeId, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "회차 본문 조회 성공(V2)", response)
        );
    }

    /**
     * 작가용 회차 단건 조회 (에디터 본문 로드 - 포인트 차감 없음)
     */
    @GetMapping("/author/novels/{novelId}/episodes/{episodeId}")
    public ResponseEntity<BaseResponse<AuthorEpisodeDetailResponse>> getAuthorEpisodeDetail(
            @PathVariable Long novelId,
            @PathVariable Long episodeId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        AuthorEpisodeDetailResponse response = episodeService.getAuthorEpisodeDetail(novelId, episodeId, userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "작가 회차 본문 조회 성공", response));
    }

    /**
     * 작가용 회차 목록 조회 (에디터용)
     * 정은식
     */
    @GetMapping("/author/novels/{novelId}/episodes")
    public ResponseEntity<BaseResponse<PageResponse<AuthorEpisodeListResponse>>> getAuthorEpisodeList(
            @PathVariable Long novelId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PageableDefault(size = 20, sort = "episodeNumber", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<AuthorEpisodeListResponse> response = episodeService.getAuthorEpisodeList(novelId, userDetails, pageable);

        return ResponseEntity.ok(
                BaseResponse.success("200", "작가 회차 목록 조회 성공", response)
        );
    }
}