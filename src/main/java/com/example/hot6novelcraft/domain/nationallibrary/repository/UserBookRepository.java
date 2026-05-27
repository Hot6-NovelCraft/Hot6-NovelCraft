package com.example.hot6novelcraft.domain.nationallibrary.repository;

import com.example.hot6novelcraft.domain.nationallibrary.entity.UserBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserBookRepository extends JpaRepository<UserBook, Long> {
    boolean existsByUserIdAndBookId(Long userId, Long bookId);

    List<UserBook> findAllByUserId(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}