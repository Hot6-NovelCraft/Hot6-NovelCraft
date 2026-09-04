package com.example.hot6novelcraft.domain.user.dto.response;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;

public record CommonUpdateResponse(

        Long userId,
        String email,
        String nickname,
        UserRole role,
        String phoneNo
) {
    public static CommonUpdateResponse of(User user) {
        return new CommonUpdateResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getPhoneNo()
        );
    }
}