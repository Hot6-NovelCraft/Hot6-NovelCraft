package com.example.hot6novelcraft.domain.user.dto.response;

import com.example.hot6novelcraft.domain.novel.entity.enums.MainGenre;
import com.example.hot6novelcraft.domain.user.entity.ReaderProfile;
import com.example.hot6novelcraft.domain.user.entity.enums.ReadingGoal;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public record ReaderUpdateResponse(
        Long readerProfileId,

        List<MainGenre> preferredGenres,

        ReadingGoal readingGoal,

        LocalDateTime updated_At

) {
    public static ReaderUpdateResponse of(ReaderProfile profile) {
        // Enum에 위임하여 안전하게 파싱 (Null, 공백, 잘못된 값 모두 방어)
        List<MainGenre> parsedGenres = (profile.getPreferredGenres() == null || profile.getPreferredGenres().isBlank())
                ? Collections.emptyList()
                : Arrays.stream(profile.getPreferredGenres().split(","))
                  .map(MainGenre::valueOf) // 👈 만들어둔 헬퍼 메서드 재사용!
                  .toList();

        return new ReaderUpdateResponse(
                profile.getId(),
                parsedGenres,
                profile.getReadingGoal(),
                profile.getUpdatedAt()
        );
    }
}
