package com.example.hot6novelcraft.domain.nationallibrary.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_books",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBook extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long bookId;

    private UserBook(Long userId, Long bookId) {
        this.userId = userId;
        this.bookId = bookId;
    }

    public static UserBook of(Long userId, Long bookId) {
        return new UserBook(userId, bookId);
    }
}