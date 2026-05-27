package com.example.hot6novelcraft.domain.user.dto.request;

import com.example.hot6novelcraft.domain.novel.entity.enums.MainGenre;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.stream.Collectors;

public record AuthorRequest(

        @NotEmpty(message = "주력 장르 선택은 필수입니다.")
        List<MainGenre> genres,

        @NotBlank
        @Size(min = 10, max = 500, message = "작가소개는 500자 이내로 작성 가능합니다.")
        String bio,

        @NotNull
        CareerLevel careerLevel,

        String instagramLinks,

        String xLinks,

        String blogLinks,

        @NotNull(message = "멘티 요청 허용 여부는 필수입니다.")
        Boolean allowMenteeRequest

) {
    // 주력 장르 변환
    public String mainGenreToString() {
        if (genres == null || genres.isEmpty())
            return null;
        return genres.stream()
                .map(MainGenre::toString)
                .collect(Collectors.joining(","));
    }
}
