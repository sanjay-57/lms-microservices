package com.lms.progress.service;

import com.lms.progress.aggregate.EventData;
import com.lms.progress.aggregate.ProgressEventTypes;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressCommandServiceTest {

    @Mock private EventStoreRepository eventStoreRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private ProgressProjector projector;
    @Mock private ContentClient contentClient;
    @Mock private EventPublisher eventPublisher;
    @InjectMocks private ProgressCommandService commandService;

    private DomainEvent studentEnrolled() {
        return DomainEvent.of(EventTypes.STUDENT_ENROLLED, "4-1", Map.of("enrollmentId", 5, "studentId", 4,
                "studentName", "Sam", "studentEmail", "sam@example.com", "courseId", 1, "courseTitle", "Java"));
    }

    private StoredEvent stored(int version, String type, Map<String, Object> data) {
        return StoredEvent.builder().aggregateId("4-1").aggregateType(StoredEvent.AGGREGATE_TYPE).eventType(type)
                .eventData(EventData.toJson(data)).eventVersion(version).createdAt(LocalDateTime.now()).build();
    }

    private List<StoredEvent> captureAppended() {
        ArgumentCaptor<StoredEvent> captor = ArgumentCaptor.forClass(StoredEvent.class);
        verify(eventStoreRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void studentEnrolled_contentServiceDown_storesFailureAndPublishesEnrollmentFailed() {
        DomainEvent event = studentEnrolled();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(eventStoreRepository.findByAggregateIdOrderByEventVersionAsc("4-1")).thenReturn(List.of());
        when(contentClient.countModules(1L)).thenThrow(new ResourceAccessException("Connection refused"));
        when(eventStoreRepository.save(any(StoredEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        commandService.handleStudentEnrolled(event);

        List<StoredEvent> appended = captureAppended();
        assertThat(appended).hasSize(1);
        assertThat(appended.get(0).getEventType()).isEqualTo(ProgressEventTypes.PROGRESS_INITIALIZATION_FAILED);
        assertThat(appended.get(0).getEventVersion()).isEqualTo(1);
        verify(projector).project(appended.get(0));

        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eq(RoutingKeys.ENROLLMENT_FAILED), published.capture());
        DomainEvent failed = published.getValue();
        assertThat(failed.eventType()).isEqualTo(EventTypes.ENROLLMENT_FAILED);
        assertThat(failed.aggregateId()).isEqualTo("4-1");
        assertThat(failed.longValue("enrollmentId")).isEqualTo(5L);
        assertThat(failed.longValue("studentId")).isEqualTo(4L);
        assertThat(failed.longValue("courseId")).isEqualTo(1L);
        assertThat(failed.stringValue("reason")).contains("Content service unavailable");
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void studentEnrolled_courseWithoutModules_publishesEnrollmentFailed() {
        DomainEvent event = studentEnrolled();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(eventStoreRepository.findByAggregateIdOrderByEventVersionAsc("4-1")).thenReturn(List.of());
        when(contentClient.countModules(1L)).thenReturn(0);
        when(eventStoreRepository.save(any(StoredEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        commandService.handleStudentEnrolled(event);

        verify(eventPublisher).publish(eq(RoutingKeys.ENROLLMENT_FAILED), any(DomainEvent.class));
    }

    @Test
    void studentEnrolled_withModules_initialisesProgressWithoutPublishing() {
        DomainEvent event = studentEnrolled();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(eventStoreRepository.findByAggregateIdOrderByEventVersionAsc("4-1")).thenReturn(List.of());
        when(contentClient.countModules(1L)).thenReturn(3);
        when(eventStoreRepository.save(any(StoredEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        commandService.handleStudentEnrolled(event);

        List<StoredEvent> appended = captureAppended();
        assertThat(appended).extracting(StoredEvent::getEventType).containsExactly(ProgressEventTypes.PROGRESS_INITIALIZED);
        assertThat(EventData.fromJson(appended.get(0).getEventData())).containsEntry("totalModules", 3);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void duplicateEvent_isIgnored() {
        DomainEvent event = studentEnrolled();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        commandService.handleStudentEnrolled(event);

        verifyNoInteractions(contentClient, eventStoreRepository, eventPublisher, projector);
    }

    @Test
    void lastModuleAfterPassedAssessment_appendsAndPublishesCourseCompleted() {
        List<StoredEvent> history = new ArrayList<>(List.of(
                stored(1, ProgressEventTypes.PROGRESS_INITIALIZED, Map.of("enrollmentId", 5, "studentId", 4,
                        "studentName", "Sam", "studentEmail", "sam@example.com", "courseId", 1,
                        "courseTitle", "Java", "totalModules", 1)),
                stored(2, ProgressEventTypes.ASSESSMENT_COMPLETED, Map.of("assessmentId", 3, "submissionId", 30,
                        "percentage", 88.0, "passed", true))));
        DomainEvent event = DomainEvent.of(EventTypes.MODULE_COMPLETED, "4-1", Map.of("moduleId", 10,
                "moduleTitle", "Intro", "courseId", 1, "studentId", 4, "completedModules", 1, "totalModules", 1));
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(eventStoreRepository.findByAggregateIdOrderByEventVersionAsc("4-1")).thenReturn(history);
        when(eventStoreRepository.save(any(StoredEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        commandService.handleModuleCompleted(event);

        List<StoredEvent> appended = captureAppended();
        assertThat(appended).extracting(StoredEvent::getEventType)
                .containsExactly(ProgressEventTypes.MODULE_COMPLETED, ProgressEventTypes.COURSE_COMPLETED);
        assertThat(appended).extracting(StoredEvent::getEventVersion).containsExactly(3, 4);

        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eq(RoutingKeys.COURSE_COMPLETED), published.capture());
        DomainEvent completed = published.getValue();
        assertThat(completed.longValue("enrollmentId")).isEqualTo(5L);
        assertThat(completed.stringValue("studentEmail")).isEqualTo("sam@example.com");
        assertThat(completed.stringValue("courseTitle")).isEqualTo("Java");
        assertThat(completed.stringValue("finalScore")).isEqualTo("88.0");
        assertThat(completed.stringValue("completionDate")).isNotBlank();
    }

    @Test
    void moduleCompleted_beforeStudentEnrolled_throwsForRetry() {
        DomainEvent event = DomainEvent.of(EventTypes.MODULE_COMPLETED, "4-1",
                Map.of("moduleId", 10, "courseId", 1, "studentId", 4, "totalModules", 2));
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(eventStoreRepository.findByAggregateIdOrderByEventVersionAsc("4-1")).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> commandService.handleModuleCompleted(event));
        verify(processedEventRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
