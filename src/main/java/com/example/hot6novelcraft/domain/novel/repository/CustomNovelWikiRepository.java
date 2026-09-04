package com.example.hot6novelcraft.domain.novel.repository;

import com.example.hot6novelcraft.domain.novel.entity.NovelWiki;

import java.util.List;

public interface CustomNovelWikiRepository {

    List<NovelWiki> findAllByNovelId(Long novelId);

}