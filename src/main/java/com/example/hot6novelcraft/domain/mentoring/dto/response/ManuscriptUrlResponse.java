package com.example.hot6novelcraft.domain.mentoring.dto.response;

public record ManuscriptUrlResponse(
        Long mentoringId,
        String manuscriptUrl
) {
    public static ManuscriptUrlResponse of(Long mentoringId, String manuscriptUrl) {
        return new ManuscriptUrlResponse(mentoringId, manuscriptUrl);
    }
}