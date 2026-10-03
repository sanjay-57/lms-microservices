package com.lms.progress.projection;

import com.lms.progress.aggregate.EventData;
import com.lms.progress.aggregate.ProgressEventTypes;
import com.lms.progress.aggregate.ProgressRules;
import com.lms.progress.entity.Progress;
import com.lms.progress.entity.ProgressStatus;
import com.lms.progress.entity.StoredEvent;
import com.lms.progress.repository.ProgressRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Projects stored events onto the {@link Progress} read model. The same code path is used for live updates
 * (inside the command transaction) and for a full rebuild from the event store, which is what guarantees
 * that a rebuilt read model is identical to the live one.
 */
@Component
@RequiredArgsConstructor
public class ProgressProjector {

    private static final Logger log = LoggerFactory.getLogger(ProgressProjector.class);

    private final ProgressRepository progressRepository;

    /** Live projection: load (or create) the row of the event's aggregate, apply the event, save. */
    public Progress project(StoredEvent event) {
        long[] ids = parseAggregateId(event.getAggregateId());
        Progress progress = progressRepository.findByStudentIdAndCourseId(ids[0], ids[1])
                .orElseGet(Progress::new);
        applyTo(progress, event);
        Progress saved = progressRepository.save(progress);
        log.debug("Projected {} v{} of {} -> status={}, percentage={}", event.getEventType(),
                event.getEventVersion(), event.getAggregateId(), saved.getStatus(), saved.getPercentage());
        return saved;
    }

    /** Pure state transition of one read-model row for one event (no persistence). */
    public void applyTo(Progress p, StoredEvent event) {
        Map<String, Object> data = EventData.fromJson(event.getEventData());
        if (p.getStudentId() == null || p.getCourseId() == null) {
            long[] ids = parseAggregateId(event.getAggregateId());
            p.setStudentId(ids[0]);
            p.setCourseId(ids[1]);
        }
        switch (event.getEventType()) {
            case ProgressEventTypes.PROGRESS_INITIALIZED -> {
                copyIdentity(p, data);
                Integer total = EventData.intValue(data, "totalModules");
                p.setTotalModules(total == null ? 0 : total);
                p.setCompletedModules(0);
                p.setPercentage(ProgressRules.percentage(0, p.getTotalModules()));
                p.setAssessmentPassed(false);
                p.setBestScore(null);
                p.setCompletedAt(null);
                p.setStatus(ProgressStatus.NOT_STARTED);
            }
            case ProgressEventTypes.PROGRESS_INITIALIZATION_FAILED -> {
                copyIdentity(p, data);
                p.setStatus(ProgressStatus.FAILED);
            }
            case ProgressEventTypes.MODULE_COMPLETED -> {
                p.setCompletedModules(p.getCompletedModules() + 1);
                Integer total = EventData.intValue(data, "totalModules");
                if (total != null && total > 0) {
                    p.setTotalModules(total);
                }
                p.setPercentage(ProgressRules.percentage(p.getCompletedModules(), p.getTotalModules()));
                refreshActiveStatus(p);
            }
            case ProgressEventTypes.ASSESSMENT_COMPLETED -> {
                p.setAssessmentPassed(Boolean.TRUE.equals(p.getAssessmentPassed()) || EventData.booleanValue(data, "passed"));
                BigDecimal score = EventData.decimalValue(data, "percentage");
                p.setBestScore(ProgressRules.max(p.getBestScore(), score == null ? null : score.setScale(2, RoundingMode.HALF_UP)));
                refreshActiveStatus(p);
            }
            case ProgressEventTypes.COURSE_COMPLETED -> {
                p.setStatus(ProgressStatus.COMPLETED);
                String date = EventData.stringValue(data, "completionDate");
                p.setCompletedAt(date == null ? event.getCreatedAt() : LocalDateTime.parse(date));
            }
            default -> throw new IllegalStateException("Unknown event type " + event.getEventType());
        }
        p.setUpdatedAt(event.getCreatedAt() == null ? LocalDateTime.now() : event.getCreatedAt());
    }

    /** "&lt;studentId&gt;-&lt;courseId&gt;" -> [studentId, courseId]. */
    public static long[] parseAggregateId(String aggregateId) {
        String[] parts = aggregateId == null ? new String[0] : aggregateId.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid aggregate id '" + aggregateId + "', expected <studentId>-<courseId>");
        }
        try {
            return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid aggregate id '" + aggregateId + "'", ex);
        }
    }

    private void refreshActiveStatus(Progress p) {
        if (p.getStatus() == ProgressStatus.COMPLETED || p.getStatus() == ProgressStatus.FAILED) {
            return;
        }
        p.setStatus(ProgressRules.activeStatus(p.getPercentage(), Boolean.TRUE.equals(p.getAssessmentPassed()), true));
    }

    private void copyIdentity(Progress p, Map<String, Object> data) {
        Long enrollmentId = EventData.longValue(data, "enrollmentId");
        if (enrollmentId != null) {
            p.setEnrollmentId(enrollmentId);
        }
        Long studentId = EventData.longValue(data, "studentId");
        if (studentId != null) {
            p.setStudentId(studentId);
        }
        Long courseId = EventData.longValue(data, "courseId");
        if (courseId != null) {
            p.setCourseId(courseId);
        }
        String studentName = EventData.stringValue(data, "studentName");
        if (studentName != null) {
            p.setStudentName(studentName);
        }
        String studentEmail = EventData.stringValue(data, "studentEmail");
        if (studentEmail != null) {
            p.setStudentEmail(studentEmail);
        }
        String courseTitle = EventData.stringValue(data, "courseTitle");
        if (courseTitle != null) {
            p.setCourseTitle(courseTitle);
        }
    }
}
