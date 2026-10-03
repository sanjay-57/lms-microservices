package com.lms.enrollment.service;

import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.entity.EnrollmentStatus;
import com.lms.enrollment.entity.ProcessedEvent;
import com.lms.enrollment.event.DomainEvent;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consumer side of the Enrollment Saga.
 * - EnrollmentFailed  : compensating action, ACTIVE -> CANCELLED
 * - CourseCompleted   : happy-path end of the journey, ACTIVE -> COMPLETED
 * Both handlers are idempotent (processed_events table), so redelivered messages do no harm.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentSagaService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentSagaService.class);

    private final EnrollmentRepository enrollmentRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void handleEnrollmentFailed(DomainEvent event) {
        if (alreadyProcessed(event)) {
            return;
        }
        Long enrollmentId = event.longValue("enrollmentId");
        if (enrollmentId == null) {
            throw new IllegalArgumentException("EnrollmentFailed without enrollmentId, eventId=" + event.eventId());
        }
        String reason = event.stringValue("reason");
        enrollmentRepository.findById(enrollmentId).ifPresentOrElse(enrollment -> {
            if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
                enrollment.setStatus(EnrollmentStatus.CANCELLED);
                enrollmentRepository.save(enrollment);
                log.warn("SAGA COMPENSATION: enrollment {} set to CANCELLED (studentId={}, courseId={}). Reason: {}",
                        enrollmentId, enrollment.getStudentId(), enrollment.getCourseId(), reason);
            } else {
                log.info("SAGA: enrollment {} already {}, nothing to compensate", enrollmentId, enrollment.getStatus());
            }
        }, () -> log.warn("SAGA: EnrollmentFailed for unknown enrollment {}", enrollmentId));
        markProcessed(event);
    }

    @Transactional
    public void handleCourseCompleted(DomainEvent event) {
        if (alreadyProcessed(event)) {
            return;
        }
        Long studentId = event.longValue("studentId");
        Long courseId = event.longValue("courseId");
        if (studentId == null || courseId == null) {
            throw new IllegalArgumentException("CourseCompleted without studentId/courseId, eventId=" + event.eventId());
        }
        List<Enrollment> active = enrollmentRepository
                .findByStudentIdAndCourseIdAndStatus(studentId, courseId, EnrollmentStatus.ACTIVE);
        for (Enrollment enrollment : active) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollmentRepository.save(enrollment);
            log.info("Enrollment {} marked COMPLETED (studentId={}, courseId={})", enrollment.getId(), studentId, courseId);
        }
        markProcessed(event);
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
