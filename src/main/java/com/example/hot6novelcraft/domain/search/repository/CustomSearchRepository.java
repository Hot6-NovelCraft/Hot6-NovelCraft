package com.example.hot6novelcraft.domain.search.repository;

import com.example.hot6novelcraft.domain.search.dto.IntegratedAuthorSearchResponse;
import com.example.hot6novelcraft.domain.search.dto.NovelSearchResponse;
import com.example.hot6novelcraft.domain.search.dto.TagGroupSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomSearchRepository {

    // 소설 제목 검색 - 표지, 제목, 작가, 장르
    Page<NovelSearchResponse> searchNovelsByTitle(String keyword, Pageable pageable, boolean isAdult);

    // 태그 검색 - 태그별 그룹핑된 소설 목록
    List<TagGroupSearchResponse> searchNovelsByTags(List<String> tags, boolean isAdult);

    // 작가 검색 - 작가 목록 + 소설 제목 검색 통합
    IntegratedAuthorSearchResponse searchByAuthorKeyword(String keyword, boolean isAdult);
}
