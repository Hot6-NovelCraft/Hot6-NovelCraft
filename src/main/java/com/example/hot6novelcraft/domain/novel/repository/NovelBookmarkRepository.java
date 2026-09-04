package com.example.hot6novelcraft.domain.novel.repository;

import com.example.hot6novelcraft.domain.novel.entity.NovelBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NovelBookmarkRepository extends JpaRepository<NovelBookmark, Long> {

    // 이미 찜 했는지 확인
    Optional<NovelBookmark> findByUserIdAndNovelId(Long userId, Long novelId);
}