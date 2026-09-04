package com.example.hot6novelcraft.domain.notification.dto.response;

import com.example.hot6novelcraft.domain.notification.entity.Notification;
import com.example.hot6novelcraft.domain.notification.entity.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        Long referenceId,
        String referenceType,
        @JsonProperty("isRead") boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getReferenceId(),
                notification.getReferenceType(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
