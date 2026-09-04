package com.example.hot6novelcraft.domain.novel.dto.request;

import com.example.hot6novelcraft.domain.novel.entity.enums.MainGenre;
import com.example.hot6novelcraft.domain.novel.entity.enums.MainTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.stream.Collectors;

public record NovelCreateRequest(

        @NotBlank(message = "소설 제목은 필수입니다.")
        @Size(max = 20, message = "소설 제목은 최대 20자까지 입력 가능합니다.")
        String title,

        @NotBlank(message = "소설 소개는 필수입니다.")
        String description,

        @NotNull(message = "장르는 필수입니다.")
        MainGenre genre,

        @NotNull(message = "태그는 필수입니다.")
        @Size(min = 1, message = "태그는 최소 1개 이상 선택해야 합니다.")
        List<@NotNull(message = "태그에 null 값을 포함할 수 없습니다.") MainTag> tags

) {
    // 태그 변환
    public String tagsToString() {
        if (tags == null || tags.isEmpty()) return null;
        return tags.stream()
                .map(MainTag::toString)
                .collect(Collectors.joining(","));
    }
}