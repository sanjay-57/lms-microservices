package com.lms.notification.service;

import com.lms.notification.dto.NotificationResponse;
import com.lms.notification.dto.UnreadCountResponse;
import com.lms.notification.entity.Notification;
import com.lms.notification.entity.ProcessedEvent;
import com.lms.notification.event.DomainEvent;
import com.lms.notification.exception.ResourceNotFoundException;
import com.lms.notification.mail.EmailSender;
import com.lms.notification.repository.NotificationRepository;
import com.lms.notification.repository.ProcessedEventRepository;
import com.lms.notification.security.AuthUser;
import com.lms.notification.security.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationMessageFactory messageFactory;
    private final EmailSender emailSender;

    /**
     * StudentEnrolled / CourseCompleted / CertificateGenerated -> store an in-app notification and send
     * (or log) the e-mail. Idempotent through the processed_events table.
     */
    @Transactional
    public void handleEvent(DomainEvent event) {
        if (alreadyProcessed(event)) {
            return;
        }
        Optional<NotificationContent> content = messageFactory.build(event);
        if (content.isEmpty()) {
            log.warn("No notification defined for event type {}", event.eventType());
            return;
        }
        NotificationContent c = content.get();
        Notification notification = notificationRepository.save(Notification.builder()
                .userId(c.userId())
                .type(c.type())
                .subject(truncate(c.subject(), 200))
                .message(c.message())
                .recipientEmail(c.recipientEmail())
                .read(false)
                .sentAt(LocalDateTime.now())
                .build());
        notification.setStatus(emailSender.send(c.recipientEmail(), c.subject(), c.message()));
        notificationRepository.save(notification);
        log.info("Notification {} stored: userId={} type={} status={}",
                notification.getId(), c.userId(), c.type(), notification.getStatus());
        markProcessed(event);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getByUser(Long userId, AuthUser current) {
        requireSelfOrAdmin(userId, current);
        return notificationRepository.findByUserIdOrderBySentAtDescIdDesc(userId)
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId, AuthUser current) {
        requireSelfOrAdmin(userId, current);
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadFalse(userId));
    }

    @Transactional
    public NotificationResponse markRead(Long id, AuthUser current) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id " + id));
        if (current.getRole() != Role.ADMIN && !notification.getUserId().equals(current.getId())) {
            throw new AccessDeniedException("You can only update your own notifications");
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAll() {
        return notificationRepository.findAllByOrderBySentAtDescIdDesc()
                .stream().map(NotificationResponse::from).toList();
    }

    private void requireSelfOrAdmin(Long userId, AuthUser current) {
        if (current.getRole() != Role.ADMIN && !current.getId().equals(userId)) {
            throw new AccessDeniedException("You can only view your own notifications");
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private boolean alreadyProcessed(DomainEvent event) {
        if (event.eventId() == null) {
            throw new IllegalArgumentException("Event without eventId");
        }
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Duplicate event ignored: type={} eventId={}", event.eventType(), event.eventId());
            return true;
        }
        return false;
    }

    private void markProcessed(DomainEvent event) {
        processedEventRepository.save(new ProcessedEvent(event.eventId(), event.eventType(), LocalDateTime.now()));
    }
}
