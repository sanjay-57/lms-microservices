package com.lms.progress.projection;

import com.lms.progress.aggregate.EventData;
import com.lms.progress.aggregate.ProgressEventTypes;
import com.lms.progress.entity.Progress;
import com.lms.progress.entity.ProgressStatus;
import com.lms.progress.entity.StoredEvent;
import com.lms.progress.repository.ProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ProgressProjectorTest {

    @Mock private ProgressRepository progressRepository;
    @InjectMocks private ProgressProjector projector;

    private int version;

    private StoredEvent event(String type, Map<String, Object> data) {
        return StoredEvent.builder().aggregateId("4-1").eventType(type).eventData(EventData.toJson(data))
                .eventVersion(++version).createdAt(LocalDateTime.of(2026, 10, 3, 12, 0)).build();
    }

    @Test
    void replayingEvents_producesExpectedReadModelRow() {
        Progress p = new Progress();
        projector.applyTo(p, event(ProgressEventTypes.PROGRESS_INITIALIZED, Map.of("enrollmentId", 5, "studentId", 4,
                "studentName", "Sam", "studentEmail", "s@x", "courseId", 1, "courseTitle", "Java", "totalModules", 2)));
        assertThat(p.getStatus()).isEqualTo(ProgressStatus.NOT_STARTED);

        projector.applyTo(p, event(ProgressEventTypes.MODULE_COMPLETED, Map.of("moduleId", 10, "totalModules", 2)));
        projector.applyTo(p, event(ProgressEventTypes.MODULE_COMPLETED, Map.of("moduleId", 11, "totalModules", 2)));
        assertThat(p.getPercentage()).isEqualByComparingTo("100");
        assertThat(p.getStatus()).isEqualTo(ProgressStatus.AWAITING_ASSESSMENT);

        projector.applyTo(p, event(ProgressEventTypes.ASSESSMENT_COMPLETED, Map.of("percentage", 82.5, "passed", true)));
        projector.applyTo(p, event(ProgressEventTypes.COURSE_COMPLETED,
                Map.of("finalScore", 82.5, "completionDate", "2026-10-03T12:00:05")));

        assertThat(p.getStudentId()).isEqualTo(4L);
        assertThat(p.getCourseId()).isEqualTo(1L);
        assertThat(p.getEnrollmentId()).isEqualTo(5L);
        assertThat(p.getCompletedModules()).isEqualTo(2);
        assertThat(p.getAssessmentPassed()).isTrue();
        assertThat(p.getBestScore()).isEqualByComparingTo("82.50");
        assertThat(p.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(p.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 3, 12, 0, 5));
    }

    @Test
    void parseAggregateId_rejectsMalformedIds() {
        assertThat(ProgressProjector.parseAggregateId("4-1")).containsExactly(4L, 1L);
        assertThrows(IllegalArgumentException.class, () -> ProgressProjector.parseAggregateId("abc"));
    }
}
