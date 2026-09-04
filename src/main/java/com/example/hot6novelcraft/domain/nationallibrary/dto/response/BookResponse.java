package com.example.hot6novelcraft.domain.nationallibrary.dto.response;

import com.example.hot6novelcraft.domain.nationallibrary.entity.Book;

public record BookResponse(
        Long id,
        String isbn,
        String title,
        String author,
        String publisher,
        String publishYear
) {
    public static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                book.getPublishYear()
        );
    }
}
