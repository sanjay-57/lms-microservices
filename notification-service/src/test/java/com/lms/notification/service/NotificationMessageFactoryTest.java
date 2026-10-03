package com.lms.notification.service;

import com.lms.notification.entity.NotificationType;
import com.lms.notification.event.DomainEvent;
import com.lms.notification.event.EventTypes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationMessageFactoryTest {

    private final NotificationMessageFactory factory = new NotificationMessageFactory("http://localhost:5173");

    @Test
    void studentEnrolled_mapsToEnrollment() {
        DomainEvent event = DomainEvent.of(EventTypes.STUDENT_ENROLLED, "4-1", Map.of("enrollmentId", 9,
                "studentId", 4, "studentName", "Alice", "studentEmail", "alice@lms.local",
                "courseId", 1, "courseTitle", "Microservices"));

        NotificationContent c = factory.build(event).orElseThrow();

        assertThat(c.type()).isEqualTo(NotificationType.ENROLLMENT);
        assertThat(c.userId()).isEqualTo(4L);
        assertThat(c.recipientEmail()).isEqualTo("alice@lms.local");
        assertThat(c.subject()).contains("Microservices");
        assertThat(c.message()).contains("Alice").contains("Microservices");
    }

    @Test
    void courseCompleted_mapsToCourseCompletedWithScore() {
        DomainEvent event = DomainEvent.of(EventTypes.COURSE_COMPLETED, "4-1", Map.of(
                "studentId", 4, "studentName", "Alice", "studentEmail", "alice@lms.local",
                "courseId", 1, "courseTitle", "Microservices", "finalScore", 87.5,
                "completionDate", "2026-10-02T18:30:00"));

        NotificationContent c = factory.build(event).orElseThrow();

        assertThat(c.type()).isEqualTo(NotificationType.COURSE_COMPLETED);
        assertThat(c.subject()).contains("Microservices");
        assertThat(c.message()).contains("87.50%");
    }

    @Test
    void certificateGenerated_mapsToCertificateIssuedWithNumberAndLink() {
        DomainEvent event = DomainEvent.of(EventTypes.CERTIFICATE_GENERATED, "4-1", Map.of(
                "certificateId", 42, "certificateNumber", "LMS-2026-000042", "verificationCode", "abc",
                "studentId", 4, "studentName", "Alice", "studentEmail", "alice@lms.local",
                "courseId", 1, "courseTitle", "Microservices", "certificateUrl", "/api/certificates/42/download"));

        NotificationContent c = factory.build(event).orElseThrow();

        assertThat(c.type()).isEqualTo(NotificationType.CERTIFICATE_ISSUED);
        assertThat(c.subject()).contains("Microservices");
        assertThat(c.message()).contains("LMS-2026-000042").contains("http://localhost:5173/certificates");
    }

    @Test
    void unrelatedEvent_isEmpty() {
        DomainEvent event = DomainEvent.of(EventTypes.MODULE_COMPLETED, "4-1", Map.of("studentId", 4));
        assertThat(factory.build(event)).isEmpty();
    }

    @Test
    void missingStudentId_throwsSoMessageGoesToDlq() {
        DomainEvent event = DomainEvent.of(EventTypes.STUDENT_ENROLLED, "4-1", Map.of("courseTitle", "X"));
        assertThrows(IllegalArgumentException.class, () -> factory.build(event));
    }
}
