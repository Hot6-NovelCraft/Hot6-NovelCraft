package com.example.hot6novelcraft.domain.user.dto.response;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;

import java.time.LocalDateTime;

public record SignupResponse(

        Long userId,
        String email,
        String nickname,
        String phoneNo,
        UserRole role,
        LocalDateTime createdAt
) {
    public static SignupResponse of(User user) {
        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhoneNo(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

}
