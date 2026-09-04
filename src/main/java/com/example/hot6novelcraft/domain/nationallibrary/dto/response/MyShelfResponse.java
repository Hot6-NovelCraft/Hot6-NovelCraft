package com.example.hot6novelcraft.domain.nationallibrary.dto.response;

import com.example.hot6novelcraft.domain.nationallibrary.entity.Book;
import com.example.hot6novelcraft.domain.nationallibrary.entity.UserBook;

public record MyShelfResponse(
        Long userBookId,
        String title,
        String author,
        String coverImageUrl
) {
    public static MyShelfResponse of(UserBook userBook, Book book) {
        return new MyShelfResponse(
                userBook.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCoverImageUrl()
        );
    }
}
