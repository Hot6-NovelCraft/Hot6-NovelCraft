package com.example.hot6novelcraft.domain.nationallibrary.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BookSearchRequest(
        @NotBlank(message = "검색어를 입력해 주세요")
        String query,

        @Min(value = 1, message = "페이지는 1 이상이어야 합니다")
        int page,

        @Min(value = 1) @Max(value = 100)
        int size
) {
    public BookSearchRequest {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
    }
}