package com.lms.notification.service;

import com.lms.notification.entity.NotificationType;

/** What to tell whom, derived from one domain event. */
public record NotificationContent(Long userId, String recipientEmail, NotificationType type,
                                  String subject, String message) { }
