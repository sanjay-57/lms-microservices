package com.lms.notification.service;

import com.lms.notification.entity.NotificationType;
import com.lms.notification.event.DomainEvent;
import com.lms.notification.event.EventTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Turns a domain event into a friendly subject/message. All data comes from the event payload, so no calls to
 * other services are needed. Returns empty for event types this service does not notify about.
 */
@Component
public class NotificationMessageFactory {

    private final String frontendBaseUrl;

    public NotificationMessageFactory(@Value("${notification.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
    }

    public Optional<NotificationContent> build(DomainEvent event) {
        Long userId = event.longValue("studentId");
        if (userId == null) {
            throw new IllegalArgumentException(event.eventType() + " without studentId, eventId=" + event.eventId());
        }
        String email = event.stringValue("studentEmail");
        String name = orDefault(event.stringValue("studentName"), "there");
        String course = orDefault(event.stringValue("courseTitle"), "your course");

        return Optional.ofNullable(switch (event.eventType()) {
            case EventTypes.STUDENT_ENROLLED -> new NotificationContent(userId, email, NotificationType.ENROLLMENT,
                    "Enrollment confirmed: " + course,
                    "Hi " + name + ",\n\nYou are now enrolled in \"" + course + "\". "
                            + "Work through the modules and pass the assessment to complete the course "
                            + "and earn your certificate.\n\nHappy learning!\nThe LMS Team");
            case EventTypes.COURSE_COMPLETED -> new NotificationContent(userId, email, NotificationType.COURSE_COMPLETED,
                    "Congratulations! You completed " + course,
                    "Hi " + name + ",\n\nCongratulations on completing \"" + course + "\""
                            + scoreSuffix(event.payload() == null ? null : event.payload().get("finalScore")) + ". "
                            + "Your certificate is being prepared and you will be notified as soon as it is issued."
                            + "\n\nWell done!\nThe LMS Team");
            case EventTypes.CERTIFICATE_GENERATED -> new NotificationContent(userId, email,
                    NotificationType.CERTIFICATE_ISSUED,
                    "Your certificate for " + course + " is ready",
                    "Hi " + name + ",\n\nYour certificate of completion for \"" + course + "\" has been issued.\n"
                            + "Certificate number: " + orDefault(event.stringValue("certificateNumber"), "-") + "\n\n"
                            + "View and download it here: " + frontendBaseUrl + "/certificates"
                            + "\n\nThe LMS Team");
            default -> null;
        });
    }

    private static String scoreSuffix(Object score) {
        if (score == null) {
            return "";
        }
        try {
            return " with a final score of "
                    + new BigDecimal(score.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
