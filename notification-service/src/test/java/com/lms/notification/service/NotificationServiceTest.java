package com.lms.notification.service;

import com.lms.notification.dto.NotificationResponse;
import com.lms.notification.entity.Notification;
import com.lms.notification.entity.NotificationStatus;
import com.lms.notification.entity.NotificationType;
import com.lms.notification.entity.ProcessedEvent;
import com.lms.notification.event.DomainEvent;
import com.lms.notification.event.EventTypes;
import com.lms.notification.mail.EmailSender;
import com.lms.notification.repository.NotificationRepository;
import com.lms.notification.repository.ProcessedEventRepository;
import com.lms.notification.security.AuthUser;
import com.lms.notification.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock private JavaMailSender mailSender;

    private final NotificationMessageFactory factory = new NotificationMessageFactory("http://localhost:5173");

    private NotificationService service(boolean mailEnabled) {
        return new NotificationService(notificationRepository, processedEventRepository, factory,
                new EmailSender(mailSenderProvider, mailEnabled, "no-reply@lms.local"));
    }

    @BeforeEach
    void saveReturnsArgument() {
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private DomainEvent event(String type) {
        return DomainEvent.of(type, "4-1", Map.of("studentId", 4, "studentName", "Alice",
                "studentEmail", "alice@lms.local", "courseId", 1, "courseTitle", "Microservices",
                "certificateNumber", "LMS-2026-000042"));
    }

    private Notification lastSaved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void eachEventType_isMappedToTheRightNotificationType() {
        Map<String, NotificationType> expected = Map.of(
                EventTypes.STUDENT_ENROLLED, NotificationType.ENROLLMENT,
                EventTypes.COURSE_COMPLETED, NotificationType.COURSE_COMPLETED,
                EventTypes.CERTIFICATE_GENERATED, NotificationType.CERTIFICATE_ISSUED);
        NotificationService service = service(false);

        expected.forEach((eventType, notificationType) -> {
            clearInvocations(notificationRepository);
            service.handleEvent(event(eventType));
            Notification saved = lastSaved();
            assertThat(saved.getType()).isEqualTo(notificationType);
            assertThat(saved.getUserId()).isEqualTo(4L);
            assertThat(saved.getRecipientEmail()).isEqualTo("alice@lms.local");
            assertThat(saved.isRead()).isFalse();
        });
        verify(processedEventRepository, times(3)).save(any(ProcessedEvent.class));
    }

    @Test
    void mailDisabled_notificationIsLogged() {
        service(false).handleEvent(event(EventTypes.STUDENT_ENROLLED));

        assertThat(lastSaved().getStatus()).isEqualTo(NotificationStatus.LOGGED);
        verifyNoInteractions(mailSender);
    }

    @Test
    void mailEnabled_notificationIsSent() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        service(true).handleEvent(event(EventTypes.CERTIFICATE_GENERATED));

        assertThat(lastSaved().getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void mailFailure_notificationSavedAsFailed_noException() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> service(true).handleEvent(event(EventTypes.COURSE_COMPLETED)));

        assertThat(lastSaved().getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void duplicateEventId_isIgnored() {
        DomainEvent event = event(EventTypes.STUDENT_ENROLLED);
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        service(false).handleEvent(event);

        verifyNoInteractions(notificationRepository, mailSender);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void markRead_ownerMarksNotificationRead() {
        Notification n = Notification.builder().id(7L).userId(4L).read(false).build();
        when(notificationRepository.findById(7L)).thenReturn(Optional.of(n));

        NotificationResponse response = service(false).markRead(7L, new AuthUser(4L, "alice@lms.local", Role.STUDENT, "t"));

        assertThat(response.read()).isTrue();
    }

    @Test
    void markRead_otherUser_isDenied() {
        Notification n = Notification.builder().id(7L).userId(4L).read(false).build();
        when(notificationRepository.findById(7L)).thenReturn(Optional.of(n));

        assertThrows(AccessDeniedException.class,
                () -> service(false).markRead(7L, new AuthUser(5L, "bob@lms.local", Role.INSTRUCTOR, "t")));
    }

    @Test
    void unreadCount_selfOrAdminOnly() {
        when(notificationRepository.countByUserIdAndReadFalse(4L)).thenReturn(3L);
        NotificationService service = service(false);

        assertThat(service.unreadCount(4L, new AuthUser(4L, "a", Role.STUDENT, "t")).count()).isEqualTo(3L);
        assertThat(service.unreadCount(4L, new AuthUser(1L, "admin", Role.ADMIN, "t")).count()).isEqualTo(3L);
        assertThrows(AccessDeniedException.class,
                () -> service.unreadCount(4L, new AuthUser(5L, "b", Role.STUDENT, "t")));
    }
}
