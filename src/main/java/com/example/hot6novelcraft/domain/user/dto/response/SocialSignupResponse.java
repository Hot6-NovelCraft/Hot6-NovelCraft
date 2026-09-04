package com.example.hot6novelcraft.domain.user.dto.response;

public record SocialSignupResponse(

        String tempToken,
        String email,
        String nickname
) {
    public static SocialSignupResponse of(String tempToken, String email, String nickname) {
        return new SocialSignupResponse(tempToken, email, nickname);
    }
}
