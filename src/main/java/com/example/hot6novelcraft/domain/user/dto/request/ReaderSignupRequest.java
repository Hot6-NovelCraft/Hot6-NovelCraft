package com.example.hot6novelcraft.domain.user.dto.request;

import com.example.hot6novelcraft.domain.novel.entity.enums.MainGenre;
import com.example.hot6novelcraft.domain.user.entity.enums.ReadingGoal;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.stream.Collectors;

public record ReaderSignupRequest(

        @NotEmpty(message = "선호 장르 선택은 필수입니다")
        @Size(min = 1, message = "중복 선택이 가능합니다.")
        List<MainGenre> preferredGenres,

        ReadingGoal readingGoal,

        Boolean notifyNewNovel,

        Boolean notifyEvent
) {

    // 선호 장르 반환
    public String mainGenreToString() {
        if (preferredGenres == null || preferredGenres.isEmpty())
            return null;
        return preferredGenres.stream()
                .map(MainGenre::toString)
                .collect(Collectors.joining(","));
    }

}
