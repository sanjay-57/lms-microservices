package com.lms.enrollment.service;

import com.lms.enrollment.client.CourseClient;
import com.lms.enrollment.client.CourseInfo;
import com.lms.enrollment.client.UserClient;
import com.lms.enrollment.client.UserInfo;
import com.lms.enrollment.dto.EnrollmentRequest;
import com.lms.enrollment.dto.EnrollmentResponse;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.entity.EnrollmentStatus;
import com.lms.enrollment.event.DomainEvent;
import com.lms.enrollment.event.EventTypes;
import com.lms.enrollment.event.RoutingKeys;
import com.lms.enrollment.exception.BadRequestException;
import com.lms.enrollment.exception.DuplicateResourceException;
import com.lms.enrollment.exception.ResourceNotFoundException;
import com.lms.enrollment.messaging.EventPublisher;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.security.AuthUser;
import com.lms.enrollment.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private CourseClient courseClient;
    @Mock private UserClient userClient;
    @Mock private EventPublisher eventPublisher;
    @InjectMocks private EnrollmentService enrollmentService;

    private final AuthUser student = new AuthUser(4L, "student1@example.com", Role.STUDENT, "token");
    private final AuthUser instructor = new AuthUser(2L, "instructor@example.com", Role.INSTRUCTOR, "token");
    private final AuthUser admin = new AuthUser(1L, "admin@example.com", Role.ADMIN, "token");

    @Test
    void enroll_success_savesEnrollmentAndPublishesStudentEnrolled() {
        when(courseClient.getCourse(1L, "token")).thenReturn(new CourseInfo(1L, "Java", 2L, "PUBLISHED"));
        when(enrollmentRepository.existsByStudentIdAndCourseIdAndStatusIn(eq(4L), eq(1L), anyCollection())).thenReturn(false);
        when(userClient.getUser(4L, "token")).thenReturn(new UserInfo(4L, "Sam", "student1@example.com"));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> {
            Enrollment e = inv.getArgument(0);
            e.setId(77L);
            return e;
        });

        EnrollmentResponse response = enrollmentService.enroll(new EnrollmentRequest(null, 1L), student);

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.status()).isEqualTo(EnrollmentStatus.ACTIVE);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eq(RoutingKeys.STUDENT_ENROLLED), captor.capture());
        DomainEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(EventTypes.STUDENT_ENROLLED);
        assertThat(event.aggregateId()).isEqualTo("4-1");
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.longValue("enrollmentId")).isEqualTo(77L);
        assertThat(event.longValue("studentId")).isEqualTo(4L);
        assertThat(event.longValue("courseId")).isEqualTo(1L);
        assertThat(event.stringValue("studentEmail")).isEqualTo("student1@example.com");
    }

    @Test
    void enroll_alreadyEnrolled_throwsConflictAndPublishesNothing() {
        when(courseClient.getCourse(1L, "token")).thenReturn(new CourseInfo(1L, "Java", 2L, "PUBLISHED"));
        when(enrollmentRepository.existsByStudentIdAndCourseIdAndStatusIn(eq(4L), eq(1L), anyCollection())).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> enrollmentService.enroll(new EnrollmentRequest(null, 1L), student));
        verifyNoInteractions(eventPublisher);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enroll_unpublishedCourse_isRejected() {
        when(courseClient.getCourse(1L, "token")).thenReturn(new CourseInfo(1L, "Java", 2L, "DRAFT"));
        assertThrows(BadRequestException.class,
                () -> enrollmentService.enroll(new EnrollmentRequest(null, 1L), student));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void enroll_courseNotFound_propagates() {
        when(courseClient.getCourse(9L, "token")).thenThrow(new ResourceNotFoundException("Course not found"));
        assertThrows(ResourceNotFoundException.class,
                () -> enrollmentService.enroll(new EnrollmentRequest(null, 9L), student));
    }

    @Test
    void enroll_studentEnrollingSomeoneElse_isForbidden() {
        assertThrows(AccessDeniedException.class,
                () -> enrollmentService.enroll(new EnrollmentRequest(5L, 1L), student));
    }

    @Test
    void enroll_asInstructor_isForbidden() {
        assertThrows(AccessDeniedException.class,
                () -> enrollmentService.enroll(new EnrollmentRequest(null, 1L), instructor));
    }

    @Test
    void enroll_asAdmin_requiresStudentId() {
        assertThrows(BadRequestException.class,
                () -> enrollmentService.enroll(new EnrollmentRequest(null, 1L), admin));
    }

    @Test
    void cancel_activeEnrollment_ownerSucceeds() {
        Enrollment e = Enrollment.builder().id(5L).studentId(4L).courseId(1L).status(EnrollmentStatus.ACTIVE).build();
        when(enrollmentRepository.findById(5L)).thenReturn(Optional.of(e));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = enrollmentService.cancel(5L, student);

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void cancel_otherStudentsEnrollment_isForbidden() {
        Enrollment e = Enrollment.builder().id(5L).studentId(9L).courseId(1L).status(EnrollmentStatus.ACTIVE).build();
        when(enrollmentRepository.findById(5L)).thenReturn(Optional.of(e));
        assertThrows(AccessDeniedException.class, () -> enrollmentService.cancel(5L, student));
    }

    @Test
    void cancel_alreadyCancelled_isConflict() {
        Enrollment e = Enrollment.builder().id(5L).studentId(4L).courseId(1L).status(EnrollmentStatus.CANCELLED).build();
        when(enrollmentRepository.findById(5L)).thenReturn(Optional.of(e));
        assertThrows(DuplicateResourceException.class, () -> enrollmentService.cancel(5L, student));
    }
}
