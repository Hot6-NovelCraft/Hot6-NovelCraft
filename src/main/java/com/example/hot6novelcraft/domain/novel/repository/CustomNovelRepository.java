package com.example.hot6novelcraft.domain.novel.repository;

import com.example.hot6novelcraft.domain.novel.dto.response.AuthorNovelListResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelDetailResponse;
import com.example.hot6novelcraft.domain.novel.dto.response.NovelListResponse;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomNovelRepository {

    // V2 - 소설 목록 조회 (QueryDSL + 필터링(상태, 태그))
    Page<NovelListResponse> findNovelListV2(String genre, NovelStatus status, Pageable pageable);

    // V2 - 신작 소설 목록 조회 (1개월 기준) - 서하나
    List<NovelListResponse> findNewNovelList(String genre, NovelStatus status, int limit);

    // 소설 상세 조회 (QueryDSL + 인덱싱)
    NovelDetailResponse findNovelDetailByNovelId(Long novelId);

    // 작가용 소설 목록 조회(본인소설, 모든상태 포함!)
    Page<AuthorNovelListResponse> findAuthorNovelList(Long authorId, Pageable pageable);

    // 최근 1시간 실시간 인기 TOP 소설 목록 조회 - 서하나
    List<Novel> findHourlyTopNovels(int limit);

    // 최근 일주일 주간 인기 TOP 소설 목록 조회 - 서하나
    List<Novel> findWeeklyTopNovels(int limit);

}
