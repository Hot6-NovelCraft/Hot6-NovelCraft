package com.example.hot6novelcraft.domain.nationallibrary.infrastructure;

import com.example.hot6novelcraft.domain.nationallibrary.dto.response.NationalLibraryApiItem;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NationalLibraryApiResponse(
        @JsonProperty("total")
        int total,          // ← 전체 결과 수

        @JsonProperty("result")
        List<NationalLibraryApiItem> result
) {
}
