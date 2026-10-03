package com.lms.progress.service;

import com.lms.progress.aggregate.CourseProgressAggregate;
import com.lms.progress.aggregate.EventData;
import com.lms.progress.aggregate.NewEvent;
import com.lms.progress.client.ContentClient;
import com.lms.progress.entity.ProcessedEvent;
import com.lms.progress.entity.StoredEvent;
import com.lms.progress.event.DomainEvent;
import com.lms.progress.event.EventTypes;
import com.lms.progress.event.RoutingKeys;
import com.lms.progress.messaging.EventPublisher;
import com.lms.progress.projection.ProgressProjector;
import com.lms.progress.repository.EventStoreRepository;
import com.lms.progress.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command side of CQRS. Every handler follows the same event-sourcing cycle inside ONE transaction:
 * <ol>
 *   <li>idempotency check (processed_events),</li>
 *   <li>load the {@link CourseProgressAggregate} by replaying its events from event_store,</li>
 *   <li>execute the command on the aggregate -> new events,</li>
 *   <li>evaluate the course-completion decision rule -> possibly CourseCompleted,</li>
 *   <li>append the new events (UNIQUE(aggregate_id, event_version) = optimistic concurrency) and project
 *       them onto the read model,</li>
 *   <li>publish integration events (EnrollmentFailed / CourseCompleted) - sent only after commit.</li>
 * </ol>
 * Any exception rolls everything back and the message is retried, then dead-lettered.
 */
@Service
@RequiredArgsConstructor
public class ProgressCommandService {

    private static final Logger log = LoggerFactory.getLogger(ProgressCommandService.class);

    private final EventStoreRepository eventStoreRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ProgressProjector projector;
    private final ContentClient contentClient;
    private final EventPublisher eventPublisher;

    /**
     * Saga step 2 (reaction to StudentEnrolled): fetch the module count from content-service.
     * >= 1 module -> ProgressInitialized; unreachable or 0 modules -> ProgressInitializationFailed and the
     * compensating integration event EnrollmentFailed (enrollment-service cancels the enrollment).
     */
    @Transactional
    public void handleStudentEnrolled(DomainEvent event) {
        if (alreadyProcessed(event)) {
            return;
        }
        Long studentId = required(event, "studentId");
        Long courseId = required(event, "courseId");
        Long enrollmentId = event.longValue("enrollmentId");
        String studentName = event.stringValue("studentName");
        String studentEmail = event.stringValue("studentEmail");
        String courseTitle = event.stringValue("courseTitle");

        CourseProgressAggregate aggregate = load(DomainEvent.aggregateId(studentId, courseId));
        if (!aggregate.canInitialize()) {
            log.info("Progress {} already exists (status={}), StudentEnrolled ignored",
                    aggregate.getAggregateId(), aggregate.status());
            markProcessed(event);
            return;
        }

        String failureReason = null;
        int totalModules = 0;
        try {
            totalModules = contentClient.countModules(courseId);
            if (totalModules < 1) {
                failureReason = "Course " + courseId + " has no modules";
            }
        } catch (RestClientException ex) {
            log.error("content-service call failed for courseId={}: {}", courseId, ex.getMessage());
            failureReason = "Content service unavailable: " + ex.getMessage();
        }

        if (failureReason != null) {
            append(aggregate, aggregate.failInitialization(enrollmentId, studentId, studentName, studentEmail,
                    courseId, courseTitle, failureReason));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enrollmentId", enrollmentId);
            payload.put("studentId", studentId);
            payload.put("courseId", courseId);
            payload.put("reason", failureReason);
            eventPublisher.publish(RoutingKeys.ENROLLMENT_FAILED,
                    DomainEvent.of(EventTypes.ENROLLMENT_FAILED, aggregate.getAggregateId(), payload));
            log.warn("SAGA: progress initialisation FAILED for {} (enrollmentId={}) -> EnrollmentFailed published. Reason: {}",
                    aggregate.getAggregateId(), enrollmentId, failureReason);
        } else {
            append(aggregate, aggregate.initialize(enrollmentId, studentId, studentName, studentEmail,
                    courseId, courseTitle, totalModules));
            log.info("SAGA: progress initialised for {} with {} module(s) (0%)", aggregate.getAggregateId(), totalModules);
            decide(aggregate);
        }
        markProcessed(event);
    }

    @Transactional
    public void handleModuleCompleted(DomainEvent event) {
        if (alreadyProcessed(event)) {
            return;
        }
        String aggregateId = DomainEvent.aggregateId(required(event, "studentId"), required(event, "courseId"));
        CourseProgressAggregate aggregate = load(aggregateId);
        List<NewEvent> changes = aggregate.completeModule(required(event, "moduleId"), event.stringValue("moduleTitle"),
                toInt(event.longValue("totalModules")));
        append(aggregate, changes);
        if (!changes.isEmpty()) {
            log.info("Module completed for {}: {}/{} modules ({}%)", aggregateId,
                    aggregate.completedModules(), aggregate.getTotalModules(), aggregate.percentage());
        }
        decide(aggregate);
        markProcessed(event);
    }

    @Transactional
    public void handleAssessmentCompleted(DomainEvent event) {
        if (alreadyProcessed(event)) {
            return;
        }
        String aggregateId = DomainEvent.aggregateId(required(event, "studentId"), required(event, "courseId"));
        CourseProgressAggregate aggregate = load(aggregateId);
        BigDecimal percentage = event.payload().get("percentage") == null
                ? null : new BigDecimal(event.stringValue("percentage"));
        boolean passed = Boolean.parseBoolean(event.stringValue("passed"));
        List<NewEvent> changes = aggregate.completeAssessment(event.longValue("assessmentId"),
                event.longValue("submissionId"), percentage, passed);
        append(aggregate, changes);
        if (!changes.isEmpty()) {
            log.info("Assessment attempt recorded for {}: percentage={}, passed={} (assessmentPassed={}, bestScore={})",
                    aggregateId, percentage, passed, aggregate.isAssessmentPassed(), aggregate.getBestScore());
        }
        decide(aggregate);
        markProcessed(event);
    }

    /**
     * Decision rule after every successful command:
     * percentage == 100 AND assessmentPassed AND not yet completed -> CourseCompleted (stored + published once).
     */
    private void decide(CourseProgressAggregate aggregate) {
        if (aggregate.isCompleted() || aggregate.isFailed() || !aggregate.isInitialized()) {
            return;
        }
        List<NewEvent> completion = aggregate.evaluateCompletion(LocalDateTime.now());
        if (completion.isEmpty()) {
            log.info("DECISION: progress={}% AND passed={} -> not completed yet ({})",
                    aggregate.percentage(), aggregate.isAssessmentPassed(), aggregate.getAggregateId());
            return;
        }
        log.info("DECISION: progress={}% AND passed={} -> CourseCompleted ({}, finalScore={})",
                aggregate.percentage(), aggregate.isAssessmentPassed(), aggregate.getAggregateId(), aggregate.getBestScore());
        append(aggregate, completion);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enrollmentId", aggregate.getEnrollmentId());
        payload.put("studentId", aggregate.getStudentId());
        payload.put("studentName", aggregate.getStudentName());
        payload.put("studentEmail", aggregate.getStudentEmail());
        payload.put("courseId", aggregate.getCourseId());
        payload.put("courseTitle", aggregate.getCourseTitle());
        payload.put("finalScore", aggregate.getBestScore());
        payload.put("completionDate", aggregate.getCompletionDate());
        eventPublisher.publish(RoutingKeys.COURSE_COMPLETED,
                DomainEvent.of(EventTypes.COURSE_COMPLETED, aggregate.getAggregateId(), payload));
    }

    private CourseProgressAggregate load(String aggregateId) {
        return CourseProgressAggregate.replay(aggregateId,
                eventStoreRepository.findByAggregateIdOrderByEventVersionAsc(aggregateId));
    }

    /** Appends new events to the event store and projects each one onto the read model. */
    private void append(CourseProgressAggregate aggregate, List<NewEvent> changes) {
        for (NewEvent change : changes) {
            StoredEvent saved = eventStoreRepository.save(StoredEvent.builder()
                    .aggregateId(aggregate.getAggregateId())
                    .aggregateType(StoredEvent.AGGREGATE_TYPE)
                    .eventType(change.eventType())
                    .eventData(EventData.toJson(change.data()))
                    .eventVersion(change.version())
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("EVENT STORE: appended {} v{} to {}", change.eventType(), change.version(), aggregate.getAggregateId());
            projector.project(saved);
        }
    }

    private static Long required(DomainEvent event, String key) {
        Long value = event.longValue(key);
        if (value == null) {
            throw new IllegalArgumentException(event.eventType() + " without " + key + ", eventId=" + event.eventId());
        }
        return value;
    }

    private static Integer toInt(Long value) {
        return value == null ? null : value.intValue();
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
