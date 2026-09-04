package com.example.hot6novelcraft.domain.nationallibrary.dto.response;

import com.example.hot6novelcraft.domain.nationallibrary.entity.Book;
import com.example.hot6novelcraft.domain.nationallibrary.entity.UserBook;

public record UserBookResponse(
        Long userBookId,
        Long bookId,
        String isbn,
        String title,
        String author,
        String publisher,
        String publishYear
) {
    public static UserBookResponse of(UserBook userBook, Book book) {
        return new UserBookResponse(
                userBook.getId(),
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                book.getPublishYear()
        );
    }
}