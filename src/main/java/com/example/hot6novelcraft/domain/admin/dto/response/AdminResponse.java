package com.example.hot6novelcraft.domain.admin.dto.response;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;

import java.time.LocalDateTime;

public record AdminResponse(
        Long userId
        , String email
        , String phoneNo
        , UserRole role
        , LocalDateTime createdAt
) {
    public static AdminResponse from(User user) {
        return new AdminResponse(
                user.getId()
                , user.getEmail()
                , user.getPhoneNo()
                , user.getRole()
                , user.getCreatedAt()
        );
    }
}
