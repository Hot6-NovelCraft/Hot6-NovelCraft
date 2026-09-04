package com.example.hot6novelcraft.domain.nationallibrary.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.BookSaveRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "books")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String publisher;

    @Column(nullable = false)
    private String publishYear;

    @Column(length = 500)
    private String coverImageUrl;   // ← 추가

    private static Book of(String isbn, String title, String author,
                           String publisher, String publishYear, String coverImageUrl) {
        Book book = new Book();
        book.isbn = isbn;
        book.title = title;
        book.author = author;
        book.publisher = publisher;
        book.publishYear = publishYear;
        book.coverImageUrl = coverImageUrl;
        return book;
    }

    public static Book from(BookSaveRequest request) {
        return of(
                request.isbn(),
                request.title(),
                request.author(),
                request.publisher(),
                request.publishYear(),
                request.coverImageUrl()
        );
    }
}