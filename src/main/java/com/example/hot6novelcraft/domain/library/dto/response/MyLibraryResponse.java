package com.example.hot6novelcraft.domain.library.dto.response;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.MyShelfResponse;

import java.util.List;

public record MyLibraryResponse(
        PageResponse<LibraryListResponse> novels,
        List<MyShelfResponse> nationalLibraryBooks
) {
}
