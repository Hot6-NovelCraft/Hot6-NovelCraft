package com.example.hot6novelcraft.domain.library.repository;

import com.example.hot6novelcraft.domain.library.entity.Library;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LibraryRepository extends JpaRepository<Library, Long> {

    Optional<Library> findByUserIdAndNovelId(Long userId, Long novelId);

    boolean existsByUserIdAndNovelId(Long userId, Long novelId);

    // AI 추천 - 서재에 담은 소설들 선호 장르 파악
    List<Library> findByUserId(Long userId);
}