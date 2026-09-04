package com.example.hot6novelcraft.domain.novel.repository;

import com.example.hot6novelcraft.domain.novel.entity.NovelWiki;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NovelWikiRepository extends JpaRepository<NovelWiki, Long>, CustomNovelWikiRepository {

}