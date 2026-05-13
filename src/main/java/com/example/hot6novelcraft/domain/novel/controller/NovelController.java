package com.example.hot6novelcraft.domain.novel.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.novel.dto.request.NovelCreateRequest;
import com.example.hot6novelcraft.domain.novel.dto.request.NovelUpdateRequest;
import com.example.hot6novelcraft.domain.novel.dto.response.*;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.service.NovelRankingService;
import com.example.hot6novelcraft.domain.novel.service.NovelService;
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

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api")
public class NovelController {

    private final NovelService novelService;
    private final NovelRankingService novelRankingService;

    /**
     * 소설 등록
     * 정은식
     */
    @PostMapping("/novels")
    public ResponseEntity<BaseResponse<NovelCreateResponse>> createNovel(
            @Valid @RequestBody NovelCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        NovelCreateResponse response = novelService.createNovel(request, userDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "소설 등록 성공", response));
    }

    /**
     * 소설 수정
     * 정은식
     */
    @PatchMapping("/novels/{novelId}")
    public ResponseEntity<BaseResponse<NovelUpdateResponse>> updateNovel(
            @PathVariable Long novelId,
            @Valid @RequestBody NovelUpdateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        NovelUpdateResponse response = novelService.updateNovel(novelId, request, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "소설 수정 성공", response)
        );
    }

    /**
     * 소설 커버 이미지 URL 저장 (AI 커버 생성 후 호출)
     */
    @PatchMapping("/novels/{novelId}/cover")
    public ResponseEntity<BaseResponse<Void>> updateNovelCoverImage(
            @PathVariable Long novelId,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        novelService.updateNovelCoverImage(novelId, body.get("coverImageUrl"), userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "커버 이미지 저장 성공", null));
    }

    /**
     * 소설 삭제
     * 정은식
     */
    @DeleteMapping("/novels/{novelId}")
    public ResponseEntity<BaseResponse<NovelDeleteResponse>> deleteNovel(
            @PathVariable Long novelId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        NovelDeleteResponse response = novelService.deleteNovel(novelId, userDetails);

        return ResponseEntity.ok(
                BaseResponse.success("200", "소설 삭제 성공", response)
        );
    }

    /**
     * 소설 목록 조회 V1
     * 정은식
     */
    @GetMapping("/v1/novels")
    public ResponseEntity<BaseResponse<PageResponse<NovelListResponse>>> getNovelListV1(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<NovelListResponse> response = novelService.getNovelListV1(pageable);

        return ResponseEntity.ok(
                BaseResponse.success("200", "소설 목록 조회 성공(V1)", response)
        );
    }

    /**
     * 소설 목록 조회 V2
     * 정은식
     */
    @GetMapping("/v2/novels")
    public ResponseEntity<BaseResponse<PageResponse<NovelListResponse>>> getNovelListV2(
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) NovelStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<NovelListResponse> response = novelService.getNovelListV2(genre, status, pageable);

        return ResponseEntity.ok(
                BaseResponse.success("200", "소설 목록 조회 성공(V2)", response)
        );
    }

    /**
     * 소설 상세 조회
     * 정은식
     */
    @GetMapping("/novels/{novelId}")
    public ResponseEntity<BaseResponse<NovelDetailResponse>> getNovelDetail(
            @PathVariable Long novelId
    ) {
        NovelDetailResponse response = novelService.getNovelDetail(novelId);

        return ResponseEntity.ok(
                BaseResponse.success("200", "소설 상세 조회 성공", response)
        );
    }

    /**
     * 작가용 소설 목록 조회 (에디터용)
     * 정은식
     */
    @GetMapping("/author/novels")
    public ResponseEntity<BaseResponse<PageResponse<AuthorNovelListResponse>>> getAuthorNovelList(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<AuthorNovelListResponse> response = novelService.getAuthorNovelList(userDetails, pageable);

        return ResponseEntity.ok(
                BaseResponse.success("200", "작가 소설 목록 조회 성공", response)
        );
    }

    /**
     * 인기 소설 TOP 5 (실시간, 주간)
     * 서하나
     */
    @GetMapping("/novels/ranking")
    public ResponseEntity<BaseResponse<List<NovelRankingResponse>>> getNovelRanking(
            @RequestParam(defaultValue = "realtime") String type
    ) {
        List<NovelRankingResponse> responses = novelRankingService.getNovelRanking(type);

        return ResponseEntity.ok(
                BaseResponse.success("200", "소설 랭킹 조회 성공", responses)
        );
    }

    /**
     * 신작용 소설 목록 조회 V2 (메인 페이지 사용)
     * 한 달 신작 리스트
     * 서하나
     */
    @GetMapping(value = "/novels", params = "type=new")
    public ResponseEntity<BaseResponse<List<NovelListResponse>>> getNewNovelList(
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) NovelStatus status,
            @RequestParam(defaultValue = "50") int limit // 기본 50개 리스트
    ) {
        // limit 값 최대 100개
        if(limit > 100) {
            limit = 100;
        }
        if(limit < 1) {
            limit = 1;
        }
        List<NovelListResponse> response = novelService.getNewNovelList(genre, status, limit);

        return ResponseEntity.ok(
                BaseResponse.success("200", "신작 소설 목록 조회 성공", response)
        );
    }
}