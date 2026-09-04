package com.example.hot6novelcraft.domain.nationallibrary.dto.response;

public record NationalLibraryBookResponse(
        String isbn,
        String title,
        String author,
        String publisher,
        String publishYear,
        String titleUrl        // 국립중앙도서관 상세페이지 URL
) {
    // 국립중앙도서관 API 원본 응답 매핑용
    public static NationalLibraryBookResponse from(NationalLibraryApiItem item) {
        return new NationalLibraryBookResponse(
                item.isbn(),
                item.title(),
                item.author(),
                item.publisher(),
                item.publishYear(),
                item.titleUrl()
        );
    }
}