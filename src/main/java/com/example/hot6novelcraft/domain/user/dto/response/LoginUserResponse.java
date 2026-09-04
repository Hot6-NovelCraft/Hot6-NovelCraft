package com.example.hot6novelcraft.domain.user.dto.response;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;

public record LoginUserResponse(

        String email,
        String nickname,
        UserRole role,
        String accessToken,
        String refreshToken
) {
    public static LoginUserResponse of(User user, String accessToken, String refreshToken) {
        return new LoginUserResponse(
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                accessToken,
                refreshToken
        );
    }
}