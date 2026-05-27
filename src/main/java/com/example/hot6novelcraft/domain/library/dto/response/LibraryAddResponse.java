package com.example.hot6novelcraft.domain.library.dto.response;

import com.example.hot6novelcraft.domain.library.entity.Library;

import java.time.LocalDateTime;

public record LibraryAddResponse(
        Long libraryId,
        String novelTitle,
        LocalDateTime addedAt
) {
    public static LibraryAddResponse from(Library library) {
        return new LibraryAddResponse(
                library.getId(),
                library.getNovelTitle(),
                library.getCreatedAt()   // BaseEntity 필드 가정
        );
    }
}