package com.example.hot6novelcraft.domain.nationallibrary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserBookSaveRequest(
        @NotBlank(message = "ISBN을 입력해 주세요")
        String isbn,

        @NotBlank(message = "제목을 입력해 주세요")
        String title,

        @NotBlank(message = "저자를 입력해 주세요")
        String author,

        @NotBlank(message = "출판사를 입력해 주세요")
        String publisher,

        @NotBlank(message = "출판년도를 입력해 주세요")
        String publishYear,

        @Size(max = 500, message = "이미지 URL은 500자를 초과할 수 없습니다")
        String coverImageUrl
) {
}
