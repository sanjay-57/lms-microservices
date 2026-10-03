package com.lms.progress.aggregate;

import com.lms.progress.entity.ProgressStatus;
import com.lms.progress.entity.StoredEvent;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Event-sourced aggregate for one student's progress in one course (aggregate id "&lt;studentId&gt;-&lt;courseId&gt;").
 * <p>
 * Plain Java, no Spring/JPA access: state is rebuilt by replaying stored events through {@link #apply(StoredEvent)}.
 * Command methods validate invariants against that state, apply the resulting events to themselves and
 * return them as {@link NewEvent}s (with the version they must be stored under). An empty list means
 * "nothing happened" (duplicate / irrelevant command).
 * <p>
 * Invariants:
 * <ul>
 *   <li>a module counts only once (duplicate module ids are ignored),</li>
 *   <li>after CourseCompleted or a failed initialisation the aggregate ignores further progress,</li>
 *   <li>ModuleCompleted / AssessmentCompleted for an aggregate that does not exist yet throws, so the message is
 *       retried (StudentEnrolled may still be on its way) and finally dead-lettered,</li>
 *   <li>assessmentPassed never goes back to false, bestScore is the maximum of all attempts,</li>
 *   <li>CourseCompleted is raised at most once.</li>
 * </ul>
 */
@Getter
public class CourseProgressAggregate {

    private static final Logger log = LoggerFactory.getLogger(CourseProgressAggregate.class);

    private final String aggregateId;
    /** Version of the last applied event; 0 = aggregate does not exist yet. */
    private int version;

    private boolean initialized;
    private boolean failed;
    private boolean completed;
    private String failureReason;

    private Long enrollmentId;
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private Long courseId;
    private String courseTitle;

    private int totalModules;
    private final Set<Long> completedModuleIds = new LinkedHashSet<>();
    private final Set<Long> assessmentSubmissionIds = new HashSet<>();
    private int assessmentAttempts;
    private boolean assessmentPassed;
    private BigDecimal bestScore;
    private String completionDate;

    public CourseProgressAggregate(String aggregateId) {
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
    }

    /** Rebuilds the aggregate by replaying its event history (must be ordered by version). */
    public static CourseProgressAggregate replay(String aggregateId, List<StoredEvent> history) {
        CourseProgressAggregate aggregate = new CourseProgressAggregate(aggregateId);
        history.forEach(aggregate::apply);
        return aggregate;
    }

    // ------------------------------------------------------------------ replay

    /** Applies one persisted event. Versions must be contiguous, otherwise the stream is corrupt. */
    public void apply(StoredEvent event) {
        if (!aggregateId.equals(event.getAggregateId())) {
            throw new IllegalArgumentException("Event of aggregate " + event.getAggregateId()
                    + " applied to aggregate " + aggregateId);
        }
        if (event.getEventVersion() == null || event.getEventVersion() != version + 1) {
            throw new IllegalStateException("Out-of-order event for aggregate " + aggregateId + ": expected version "
                    + (version + 1) + " but got " + event.getEventVersion());
        }
        mutate(event.getEventType(), EventData.fromJson(event.getEventData()));
        version = event.getEventVersion();
    }

    // ------------------------------------------------------------------ commands

    /** True if a StudentEnrolled may (re)initialise this aggregate: new, or a previous attempt failed. */
    public boolean canInitialize() {
        return !completed && (!initialized || failed);
    }

    public List<NewEvent> initialize(Long enrollmentId, Long studentId, String studentName, String studentEmail,
                                     Long courseId, String courseTitle, int totalModules) {
        if (!canInitialize()) {
            log.info("Aggregate {} already initialised (status={}), StudentEnrolled ignored", aggregateId, status());
            return List.of();
        }
        if (totalModules < 1) {
            throw new IllegalArgumentException("totalModules must be >= 1 to initialise progress");
        }
        Map<String, Object> data = identity(enrollmentId, studentId, studentName, studentEmail, courseId, courseTitle);
        data.put("totalModules", totalModules);
        return List.of(raise(ProgressEventTypes.PROGRESS_INITIALIZED, data));
    }

    public List<NewEvent> failInitialization(Long enrollmentId, Long studentId, String studentName, String studentEmail,
                                             Long courseId, String courseTitle, String reason) {
        if (!canInitialize()) {
            log.info("Aggregate {} already initialised (status={}), failure not recorded", aggregateId, status());
            return List.of();
        }
        Map<String, Object> data = identity(enrollmentId, studentId, studentName, studentEmail, courseId, courseTitle);
        data.put("reason", reason);
        return List.of(raise(ProgressEventTypes.PROGRESS_INITIALIZATION_FAILED, data));
    }

    public List<NewEvent> completeModule(Long moduleId, String moduleTitle, Integer totalModulesFromEvent) {
        requireExists("ModuleCompleted");
        if (moduleId == null) {
            throw new IllegalArgumentException("ModuleCompleted without moduleId for aggregate " + aggregateId);
        }
        if (isClosed("ModuleCompleted")) {
            return List.of();
        }
        if (completedModuleIds.contains(moduleId)) {
            log.info("Aggregate {}: module {} already completed, duplicate ignored", aggregateId, moduleId);
            return List.of();
        }
        int total = totalModulesFromEvent != null && totalModulesFromEvent > 0 ? totalModulesFromEvent : totalModules;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("moduleId", moduleId);
        data.put("moduleTitle", moduleTitle);
        data.put("totalModules", total);
        return List.of(raise(ProgressEventTypes.MODULE_COMPLETED, data));
    }

    public List<NewEvent> completeAssessment(Long assessmentId, Long submissionId, BigDecimal percentage, boolean passed) {
        requireExists("AssessmentCompleted");
        if (isClosed("AssessmentCompleted")) {
            return List.of();
        }
        if (submissionId != null && assessmentSubmissionIds.contains(submissionId)) {
            log.info("Aggregate {}: submission {} already recorded, duplicate ignored", aggregateId, submissionId);
            return List.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assessmentId", assessmentId);
        data.put("submissionId", submissionId);
        data.put("percentage", percentage);
        data.put("passed", passed);
        return List.of(raise(ProgressEventTypes.ASSESSMENT_COMPLETED, data));
    }

    /**
     * Decision rule: percentage == 100 AND assessmentPassed AND not yet completed -> CourseCompleted.
     * Called after every successful command; raises the event at most once.
     */
    public List<NewEvent> evaluateCompletion(LocalDateTime now) {
        if (!initialized || failed || completed) {
            return List.of();
        }
        if (!ProgressRules.isFullyCompleted(percentage()) || !assessmentPassed) {
            return List.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("finalScore", bestScore);
        data.put("completionDate", now.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return List.of(raise(ProgressEventTypes.COURSE_COMPLETED, data));
    }

    // ------------------------------------------------------------------ derived state

    public boolean exists() {
        return version > 0;
    }

    public int completedModules() {
        return completedModuleIds.size();
    }

    public BigDecimal percentage() {
        return ProgressRules.percentage(completedModuleIds.size(), totalModules);
    }

    public ProgressStatus status() {
        if (failed) {
            return ProgressStatus.FAILED;
        }
        if (completed) {
            return ProgressStatus.COMPLETED;
        }
        if (!initialized) {
            return ProgressStatus.NOT_STARTED;
        }
        return ProgressRules.activeStatus(percentage(), assessmentPassed,
                !completedModuleIds.isEmpty() || assessmentAttempts > 0);
    }

    // ------------------------------------------------------------------ internals

    private NewEvent raise(String eventType, Map<String, Object> data) {
        mutate(eventType, data);
        version++;
        return new NewEvent(eventType, data, version);
    }

    /** The only place where state changes - used both for replay and for newly raised events. */
    private void mutate(String eventType, Map<String, Object> data) {
        switch (eventType) {
            case ProgressEventTypes.PROGRESS_INITIALIZED -> {
                copyIdentity(data);
                totalModules = Optional.ofNullable(EventData.intValue(data, "totalModules")).orElse(0);
                initialized = true;
                failed = false;
                failureReason = null;
                completedModuleIds.clear();
                assessmentSubmissionIds.clear();
                assessmentAttempts = 0;
                assessmentPassed = false;
                bestScore = null;
            }
            case ProgressEventTypes.PROGRESS_INITIALIZATION_FAILED -> {
                copyIdentity(data);
                failed = true;
                failureReason = EventData.stringValue(data, "reason");
            }
            case ProgressEventTypes.MODULE_COMPLETED -> {
                completedModuleIds.add(EventData.longValue(data, "moduleId"));
                Integer total = EventData.intValue(data, "totalModules");
                if (total != null && total > 0) {
                    totalModules = total;
                }
            }
            case ProgressEventTypes.ASSESSMENT_COMPLETED -> {
                Long submissionId = EventData.longValue(data, "submissionId");
                if (submissionId != null) {
                    assessmentSubmissionIds.add(submissionId);
                }
                assessmentAttempts++;
                assessmentPassed = assessmentPassed || EventData.booleanValue(data, "passed");
                bestScore = ProgressRules.max(bestScore, EventData.decimalValue(data, "percentage"));
            }
            case ProgressEventTypes.COURSE_COMPLETED -> {
                completed = true;
                completionDate = EventData.stringValue(data, "completionDate");
            }
            default -> throw new IllegalStateException("Unknown event type '" + eventType + "' in aggregate " + aggregateId);
        }
    }

    private void requireExists(String command) {
        if (!exists()) {
            throw new IllegalStateException(command + " for unknown progress aggregate " + aggregateId
                    + " (StudentEnrolled not processed yet)");
        }
    }

    private boolean isClosed(String command) {
        if (failed || completed) {
            log.info("Aggregate {} is {}, {} ignored", aggregateId, status(), command);
            return true;
        }
        return false;
    }

    private static Map<String, Object> identity(Long enrollmentId, Long studentId, String studentName, String studentEmail,
                                                Long courseId, String courseTitle) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enrollmentId", enrollmentId);
        data.put("studentId", studentId);
        data.put("studentName", studentName);
        data.put("studentEmail", studentEmail);
        data.put("courseId", courseId);
        data.put("courseTitle", courseTitle);
        return data;
    }

    private void copyIdentity(Map<String, Object> data) {
        enrollmentId = EventData.longValue(data, "enrollmentId");
        studentId = EventData.longValue(data, "studentId");
        studentName = EventData.stringValue(data, "studentName");
        studentEmail = EventData.stringValue(data, "studentEmail");
        courseId = EventData.longValue(data, "courseId");
        courseTitle = EventData.stringValue(data, "courseTitle");
    }
}
