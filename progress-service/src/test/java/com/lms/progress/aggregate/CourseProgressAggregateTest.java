package com.lms.progress.aggregate;

import com.lms.progress.entity.ProgressStatus;
import com.lms.progress.entity.StoredEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CourseProgressAggregateTest {

    private static final String ID = "4-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0, 5);

    private CourseProgressAggregate initialised(int totalModules) {
        CourseProgressAggregate aggregate = new CourseProgressAggregate(ID);
        aggregate.initialize(5L, 4L, "Sam", "sam@example.com", 1L, "Java", totalModules);
        return aggregate;
    }

    /** Converts newly raised events to stored events, as the command service would persist them. */
    private static List<StoredEvent> toStored(List<NewEvent> events) {
        return events.stream().map(e -> StoredEvent.builder()
                .aggregateId(ID).aggregateType(StoredEvent.AGGREGATE_TYPE)
                .eventType(e.eventType()).eventData(EventData.toJson(e.data()))
                .eventVersion(e.version()).createdAt(NOW).build()).toList();
    }

    @Test
    void replay_rebuildsSameStateAsLiveAggregate() {
        CourseProgressAggregate live = new CourseProgressAggregate(ID);
        List<NewEvent> raised = new ArrayList<>();
        raised.addAll(live.initialize(5L, 4L, "Sam", "sam@example.com", 1L, "Java", 2));
        raised.addAll(live.completeModule(10L, "Intro", 2));
        raised.addAll(live.completeAssessment(3L, 30L, new BigDecimal("40.00"), false));
        raised.addAll(live.completeAssessment(3L, 31L, new BigDecimal("85.50"), true));

        CourseProgressAggregate replayed = CourseProgressAggregate.replay(ID, toStored(raised));

        assertThat(replayed.getVersion()).isEqualTo(4);
        assertThat(replayed.getEnrollmentId()).isEqualTo(5L);
        assertThat(replayed.getStudentEmail()).isEqualTo("sam@example.com");
        assertThat(replayed.getCompletedModuleIds()).containsExactly(10L);
        assertThat(replayed.getTotalModules()).isEqualTo(2);
        assertThat(replayed.percentage()).isEqualByComparingTo("50.00");
        assertThat(replayed.isAssessmentPassed()).isTrue();
        assertThat(replayed.getBestScore()).isEqualByComparingTo("85.50");
        assertThat(replayed.status()).isEqualTo(ProgressStatus.IN_PROGRESS).isEqualTo(live.status());
    }

    @Test
    void replay_outOfOrderVersion_isRejected() {
        CourseProgressAggregate live = new CourseProgressAggregate(ID);
        List<StoredEvent> stored = toStored(live.initialize(5L, 4L, "Sam", "s@x", 1L, "Java", 2));
        stored.get(0).setEventVersion(2);
        assertThrows(IllegalStateException.class, () -> CourseProgressAggregate.replay(ID, stored));
    }

    @Test
    void decision_allModulesAndPassed_completesExactlyOnce() {
        CourseProgressAggregate aggregate = initialised(2);
        aggregate.completeModule(10L, "M1", 2);
        aggregate.completeAssessment(3L, 30L, new BigDecimal("90"), true);
        assertThat(aggregate.evaluateCompletion(NOW)).isEmpty(); // only 50%

        aggregate.completeModule(11L, "M2", 2);
        List<NewEvent> completion = aggregate.evaluateCompletion(NOW);

        assertThat(completion).hasSize(1);
        assertThat(completion.get(0).eventType()).isEqualTo(ProgressEventTypes.COURSE_COMPLETED);
        assertThat(completion.get(0).data())
                .containsEntry("finalScore", new BigDecimal("90"))
                .containsEntry("completionDate", "2026-10-03T12:00:05");
        assertThat(aggregate.status()).isEqualTo(ProgressStatus.COMPLETED);

        // never twice, and later events are ignored
        assertThat(aggregate.evaluateCompletion(NOW)).isEmpty();
        assertThat(aggregate.completeAssessment(3L, 32L, new BigDecimal("100"), true)).isEmpty();
    }

    @Test
    void decision_allModulesWithoutPass_doesNotComplete_andAwaitsAssessment() {
        CourseProgressAggregate aggregate = initialised(2);
        aggregate.completeModule(10L, "M1", 2);
        aggregate.completeModule(11L, "M2", 2);
        aggregate.completeAssessment(3L, 30L, new BigDecimal("30"), false);

        assertThat(aggregate.percentage()).isEqualByComparingTo("100");
        assertThat(aggregate.evaluateCompletion(NOW)).isEmpty();
        assertThat(aggregate.status()).isEqualTo(ProgressStatus.AWAITING_ASSESSMENT);
    }

    @Test
    void assessmentPassedBeforeModules_completesWhenLastModuleArrives() {
        CourseProgressAggregate aggregate = initialised(1);
        aggregate.completeAssessment(3L, 30L, new BigDecimal("75"), true);
        aggregate.completeAssessment(3L, 31L, new BigDecimal("60"), false); // failed retry
        assertThat(aggregate.evaluateCompletion(NOW)).isEmpty();
        assertThat(aggregate.isAssessmentPassed()).isTrue();     // stays true
        assertThat(aggregate.getBestScore()).isEqualByComparingTo("75"); // max

        aggregate.completeModule(10L, "M1", 1);
        assertThat(aggregate.evaluateCompletion(NOW)).hasSize(1);
    }

    @Test
    void duplicateModule_isIgnored() {
        CourseProgressAggregate aggregate = initialised(4);
        assertThat(aggregate.completeModule(10L, "M1", 4)).hasSize(1);
        assertThat(aggregate.completeModule(10L, "M1", 4)).isEmpty();

        assertThat(aggregate.completedModules()).isEqualTo(1);
        assertThat(aggregate.percentage()).isEqualByComparingTo("25.00");
        assertThat(aggregate.getVersion()).isEqualTo(2);
    }

    @Test
    void moduleCompleted_forUnknownAggregate_throwsSoMessageIsRetried() {
        CourseProgressAggregate aggregate = new CourseProgressAggregate(ID);
        assertThrows(IllegalStateException.class, () -> aggregate.completeModule(10L, "M1", 2));
    }

    @Test
    void failedInitialization_ignoresProgress_andAllowsReinitialisation() {
        CourseProgressAggregate aggregate = new CourseProgressAggregate(ID);
        List<NewEvent> failed = aggregate.failInitialization(5L, 4L, "Sam", "s@x", 1L, "Java", "no modules");
        assertThat(failed.get(0).eventType()).isEqualTo(ProgressEventTypes.PROGRESS_INITIALIZATION_FAILED);
        assertThat(aggregate.status()).isEqualTo(ProgressStatus.FAILED);
        assertThat(aggregate.completeModule(10L, "M1", 2)).isEmpty();

        assertThat(aggregate.initialize(6L, 4L, "Sam", "s@x", 1L, "Java", 2)).hasSize(1);
        assertThat(aggregate.status()).isEqualTo(ProgressStatus.NOT_STARTED);
        assertThat(aggregate.getEnrollmentId()).isEqualTo(6L);
    }

    @Test
    void duplicateStudentEnrolled_doesNotReinitialise() {
        CourseProgressAggregate aggregate = initialised(2);
        aggregate.completeModule(10L, "M1", 2);
        assertThat(aggregate.initialize(5L, 4L, "Sam", "s@x", 1L, "Java", 2)).isEmpty();
        assertThat(aggregate.completedModules()).isEqualTo(1);
    }

    @Test
    void eventData_roundTripsThroughJson() {
        Map<String, Object> back = EventData.fromJson(EventData.toJson(Map.of("moduleId", 10L, "passed", true)));
        assertThat(EventData.longValue(back, "moduleId")).isEqualTo(10L);
        assertThat(EventData.booleanValue(back, "passed")).isTrue();
    }
}
