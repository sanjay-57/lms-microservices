package com.lms.enrollment.service;

import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.entity.EnrollmentStatus;
import com.lms.enrollment.entity.ProcessedEvent;
import com.lms.enrollment.event.DomainEvent;
import com.lms.enrollment.event.EventTypes;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentSagaServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @InjectMocks private EnrollmentSagaService sagaService;

    private DomainEvent failedEvent(Object enrollmentId) {
        Map<String, Object> payload = enrollmentId == null
                ? Map.of("reason", "boom")
                : Map.of("enrollmentId", enrollmentId, "reason", "Progress initialisation failed");
        return DomainEvent.of(EventTypes.ENROLLMENT_FAILED, "4-1", payload);
    }

    @Test
    void enrollmentFailed_cancelsActiveEnrollment() {
        Enrollment e = Enrollment.builder().id(5L).studentId(4L).courseId(1L).status(EnrollmentStatus.ACTIVE).build();
        DomainEvent event = failedEvent(5); // Integer, as produced by JSON deserialisation
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(enrollmentRepository.findById(5L)).thenReturn(Optional.of(e));

        sagaService.handleEnrollmentFailed(event);

        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        verify(enrollmentRepository).save(e);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void enrollmentFailed_duplicateEvent_isIgnored() {
        DomainEvent event = failedEvent(5);
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        sagaService.handleEnrollmentFailed(event);

        verifyNoInteractions(enrollmentRepository);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void enrollmentFailed_alreadyCompleted_isNotChanged() {
        Enrollment e = Enrollment.builder().id(5L).studentId(4L).courseId(1L).status(EnrollmentStatus.COMPLETED).build();
        DomainEvent event = failedEvent(5L);
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(enrollmentRepository.findById(5L)).thenReturn(Optional.of(e));

        sagaService.handleEnrollmentFailed(event);

        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enrollmentFailed_missingEnrollmentId_throwsSoMessageGoesToDlq() {
        DomainEvent event = failedEvent(null);
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> sagaService.handleEnrollmentFailed(event));
    }

    @Test
    void courseCompleted_marksActiveEnrollmentCompleted() {
        Enrollment e = Enrollment.builder().id(5L).studentId(4L).courseId(1L).status(EnrollmentStatus.ACTIVE).build();
        DomainEvent event = DomainEvent.of(EventTypes.COURSE_COMPLETED, "4-1", Map.of("studentId", 4, "courseId", 1));
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(enrollmentRepository.findByStudentIdAndCourseIdAndStatus(4L, 1L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(e));

        sagaService.handleCourseCompleted(event);

        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    }
}
