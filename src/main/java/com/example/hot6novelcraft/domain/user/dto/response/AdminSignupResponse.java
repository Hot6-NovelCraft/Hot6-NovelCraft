package com.example.hot6novelcraft.domain.user.dto.response;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;

import java.time.LocalDateTime;

public record AdminSignupResponse(

        Long userId,
        String email,
        UserRole role,
        LocalDateTime createdAt
) {
    public static AdminSignupResponse of(User user) {
        return new AdminSignupResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

}
