package com.example.hot6novelcraft.domain.library.dto.request;

import com.example.hot6novelcraft.domain.library.entity.enums.LibraryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LibraryAddRequest(

        @NotNull(message = "소설 ID는 필수입니다")
        @Positive(message = "소설 ID는 양수여야 합니다")
        Long novelId,

        @NotBlank(message = "소설 제목은 필수입니다")
        @Size(max = 200, message = "소설 제목은 200자 이하여야 합니다")
        String novelTitle,

        @NotBlank(message = "작가명은 필수입니다")
        @Size(max = 100, message = "작가명은 100자 이하여야 합니다")
        String authorNickname,

        @Size(max = 500, message = "표지 이미지 URL은 500자 이하여야 합니다")
        String coverImageUrl,

        @NotNull(message = "서재 타입은 필수입니다")
        LibraryType libraryType
) {
}