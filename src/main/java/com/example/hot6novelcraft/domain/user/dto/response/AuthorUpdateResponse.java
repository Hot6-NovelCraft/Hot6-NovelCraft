package com.example.hot6novelcraft.domain.user.dto.response;

import com.example.hot6novelcraft.domain.novel.entity.enums.MainGenre;
import com.example.hot6novelcraft.domain.user.entity.AuthorProfile;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record AuthorUpdateResponse(

        Long authorProfileId,

        List<MainGenre> mainGenreList,

        String bio,

        CareerLevel careerLevel,

        String instagramLinks,

        String xLinks,

        String blogLinks,

        boolean allowMenteeRequest,

        LocalDateTime updated_At

) {
    public static AuthorUpdateResponse of(AuthorProfile profile) {

        List<MainGenre> parsedGenres = (profile.getMainGenre() == null || profile.getMainGenre().isBlank())
                ? Collections.emptyList()
                : Arrays.stream(profile.getMainGenre().split(","))
                  .map(String::trim) // 공백 제거 (예: " ROMANCE" -> "ROMANCE")
                  .map(String::toUpperCase) // 대소문자 방어
                  .map(genreStr -> {
                      try {
                          return MainGenre.valueOf(genreStr);
                      } catch (IllegalArgumentException e) {
                          return null;
                      }
                  })
                  .filter(Objects::nonNull) // null로 반환된 잘못된 값 필터링
                  .toList();

        return new AuthorUpdateResponse(
                profile.getId(),
                parsedGenres,
                profile.getBio(),
                profile.getCareerLevel(),
                profile.getInstagramLinks(),
                profile.getXLinks(),
                profile.getBlogLinks(),
                profile.isAllowMenteeRequest(),
                profile.getUpdatedAt()
        );
    }
}
