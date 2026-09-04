package com.example.hot6novelcraft.domain.notification.entity;

import com.example.hot6novelcraft.common.entity.BaseEntity;
import com.example.hot6novelcraft.domain.notification.entity.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    private String content;

    private Long referenceId;

    private String referenceType;

    @Column(nullable = false)
    private boolean isRead;

    public static Notification create(String eventId, Long userId, NotificationType type, String title,
                                      String message, Long referenceId, String referenceType) {
        Notification notification = new Notification();
        notification.eventId = eventId;
        notification.userId = userId;
        notification.type = type;
        notification.title = title;
        notification.content = message;
        notification.referenceId = referenceId;
        notification.referenceType = referenceType;
        notification.isRead = false;
        return notification;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
