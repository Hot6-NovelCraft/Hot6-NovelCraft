package com.example.hot6novelcraft.domain.nationallibrary.repository;

import com.example.hot6novelcraft.domain.nationallibrary.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    boolean existsByIsbn(String isbn);

    Optional<Book> findByIsbn(String isbn);
}
