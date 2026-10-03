package com.lms.notification.controller;

import com.lms.notification.dto.NotificationResponse;
import com.lms.notification.dto.UnreadCountResponse;
import com.lms.notification.security.AuthUser;
import com.lms.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "In-app notifications created from LMS events (also e-mailed)")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "List all notifications (ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<NotificationResponse> all() {
        return notificationService.getAll();
    }

    @Operation(summary = "List a user's notifications, newest first (self or ADMIN)")
    @GetMapping("/user/{userId}")
    public List<NotificationResponse> byUser(@PathVariable Long userId, @AuthenticationPrincipal AuthUser current) {
        return notificationService.getByUser(userId, current);
    }

    @Operation(summary = "Number of unread notifications of a user (self or ADMIN)")
    @GetMapping("/user/{userId}/unread-count")
    public UnreadCountResponse unreadCount(@PathVariable Long userId, @AuthenticationPrincipal AuthUser current) {
        return notificationService.unreadCount(userId, current);
    }

    @Operation(summary = "Mark a notification as read (owner or ADMIN)")
    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        return notificationService.markRead(id, current);
    }
}
