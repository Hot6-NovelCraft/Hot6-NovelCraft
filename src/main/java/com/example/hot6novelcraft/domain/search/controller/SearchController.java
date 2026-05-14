package com.example.hot6novelcraft.domain.search.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.SearchExceptionEnum;
import com.example.hot6novelcraft.domain.search.dto.IntegratedAuthorSearchResponse;
import com.example.hot6novelcraft.domain.search.dto.NovelSearchResponse;
import com.example.hot6novelcraft.domain.search.dto.TagGroupSearchResponse;
import com.example.hot6novelcraft.domain.search.service.SearchService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    /** ============ V1 ===============
     1. 제목(소설) 검색
     - GET /api/search/v1/novels?keyword=바다
     2. 태그 검색
     - 복수 검색시 : GET /api/search/v1/tags?tags=FANTASY&tags=MUNCHKIN
     3. 작가 검색
     - GET /api/search/v1/authors?keyword=백산
     - 유사 닉네임 작가 목록 + 유사 키워드 제목 포함 소설 목록
     =================================== */
    @GetMapping("/v1/novels")
    public ResponseEntity<BaseResponse<PageResponse<NovelSearchResponse>>> searchNovelsV1(
            @RequestParam String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<NovelSearchResponse> result = searchService.searchNovelsV1(keyword, pageable);
        return ResponseEntity.ok(BaseResponse.success("200", "소설 제목 검색 성공(V1)", PageResponse.register(result)));
    }

    @GetMapping("/v1/tags")
    public ResponseEntity<BaseResponse<List<TagGroupSearchResponse>>> searchByTagsV1(
            @RequestParam List<String> tags,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        boolean isAdult = userDetails != null && userDetails.getUser().isAdultVerificationValid();

        List<TagGroupSearchResponse> result = searchService.searchByTagsV1(tags);
        return ResponseEntity.ok(BaseResponse.success("200", "소설 태그 검색 성공(V1)", result));
    }

    @GetMapping("/v1/authors")
    public ResponseEntity<BaseResponse<IntegratedAuthorSearchResponse>> searchAuthorsV1(
            @RequestParam String keyword
    ) {
        IntegratedAuthorSearchResponse result = searchService.searchAuthorsV1(keyword);
        return ResponseEntity.ok(BaseResponse.success("200", "작가 검색 성공", result));
    }


    /** ============ V2 =============== */
    @GetMapping("/v2/novels")
    public ResponseEntity<BaseResponse<PageResponse<NovelSearchResponse>>> searchNovels(
            @RequestParam String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if(keyword == null || keyword.trim().isEmpty()) {
            throw new ServiceErrorException(SearchExceptionEnum.ERR_SEARCH_KEYWORD_EMPTY);
        }
        Page<NovelSearchResponse> result = searchService.searchNovels(keyword.trim(), pageable, userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "소설 제목 검색 성공", PageResponse.register(result)));
    }

    @GetMapping("/v2/tags")
    public ResponseEntity<BaseResponse<List<TagGroupSearchResponse>>> searchTags(
            @RequestParam List<String> tags,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        boolean isAdult = userDetails != null && userDetails.getUser().isAdultVerificationValid();

        List<TagGroupSearchResponse> result = searchService.searchByTags(tags, userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "소설 태그 검색 성공", result));
    }

    @GetMapping("/v2/authors")
    public ResponseEntity<BaseResponse<IntegratedAuthorSearchResponse>> searchAuthors(
            @RequestParam String keyword,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if(keyword == null || keyword.trim().isEmpty()) {
            throw new ServiceErrorException(SearchExceptionEnum.ERR_SEARCH_KEYWORD_EMPTY);
        }
        IntegratedAuthorSearchResponse result = searchService.searchAuthors(keyword.trim(), userDetails);
        return ResponseEntity.ok(BaseResponse.success("200", "작가 검색 성공", result));
    }

    /** ============ 인기 검색어 랭킹 =============== */
    @GetMapping("/keywords/popular")
    public ResponseEntity<BaseResponse<List<String>>> getPopularKeywords() {
        return ResponseEntity.ok(BaseResponse.success("200", "인기 검색어 조회 성공", searchService.getTopSearchKeywords()));
    }

    /** ============ 인기 테그 랭킹 =============== */
    @GetMapping("/tags/popular")
    public ResponseEntity<BaseResponse<List<String>>> getPopularTags() {
        return ResponseEntity.ok(BaseResponse.success("200", "인기 태그 조회 성공", searchService.getTopTagsKeywords()));
    }

    /** ===== 내 최근 검색어 조회 (로그인 필수) ====== */
    @GetMapping("/keywords/recent")
    public ResponseEntity<BaseResponse<List<String>>> getRecentSearchKeywords(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if(userDetails == null) {
            throw new ServiceErrorException(SearchExceptionEnum.ERR_SIGN_IN_SERVICE);
        }
        return ResponseEntity.ok(BaseResponse.success("200", "최근 검색어 조회 성공",
                searchService.getRecentSearchKeywords(userDetails.getUser().getId())));
    }
}
