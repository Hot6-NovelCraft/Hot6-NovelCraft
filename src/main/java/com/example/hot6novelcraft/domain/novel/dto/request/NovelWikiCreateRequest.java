package com.example.hot6novelcraft.domain.novel.dto.request;

import com.example.hot6novelcraft.domain.novel.entity.enums.WikiCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NovelWikiCreateRequest(

        @NotNull(message = "카테고리는 필수입니다.")
        WikiCategory category,

        @NotBlank(message = "설정집 제목은 필수입니다.")
        @Size(max = 20, message = "설정집 제목은 최대 20자까지 입력 가능합니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 1000, message = "설정집 내용은 최대 1000자까지 입력 가능합니다.")
        String content

) {
}