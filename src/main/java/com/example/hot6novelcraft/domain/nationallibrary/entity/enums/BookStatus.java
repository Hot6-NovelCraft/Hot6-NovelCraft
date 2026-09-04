package com.example.hot6novelcraft.domain.nationallibrary.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BookStatus {
    SAVED("저장됨"),
    DELETED("삭제됨");

    private final String description;
}
