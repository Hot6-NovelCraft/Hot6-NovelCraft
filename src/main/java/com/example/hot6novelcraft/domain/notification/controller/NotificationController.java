package com.example.hot6novelcraft.domain.notification.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.notification.dto.response.NotificationResponse;
import com.example.hot6novelcraft.domain.notification.service.NotificationService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Long userId = userDetails.getUser().getId();
        PageResponse<NotificationResponse> response = notificationService.getNotifications(
                userId, PageRequest.of(page - 1, size));
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "알림 목록 조회 성공", response));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<BaseResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Long userId = userDetails.getUser().getId();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "읽지 않은 알림 수 조회 성공",
                Map.of("count", count)));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<BaseResponse<NotificationResponse>> markAsRead(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long notificationId
    ) {
        Long userId = userDetails.getUser().getId();
        NotificationResponse response = notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "알림 읽음 처리 완료", response));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<BaseResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Long userId = userDetails.getUser().getId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "모든 알림 읽음 처리 완료", null));
    }
}
