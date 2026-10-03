package com.lms.notification.dto;

import com.lms.notification.entity.Notification;
import com.lms.notification.entity.NotificationStatus;
import com.lms.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, Long userId, NotificationType type, String subject, String message,
                                   String recipientEmail, NotificationStatus status, boolean read,
                                   LocalDateTime sentAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getUserId(), n.getType(), n.getSubject(), n.getMessage(),
                n.getRecipientEmail(), n.getStatus(), n.isRead(), n.getSentAt());
    }
}
