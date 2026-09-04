package com.example.hot6novelcraft.domain.search.dto;

import java.util.List;

public record TagGroupSearchResponse(

        String tag
        , List<NovelSimpleResponse> novels
) {
}
