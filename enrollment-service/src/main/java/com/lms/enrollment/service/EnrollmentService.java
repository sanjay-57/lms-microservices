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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseClient courseClient;
    private final UserClient userClient;
    private final EventPublisher eventPublisher;

    /**
     * Step 1 of the Enrollment Saga: create the enrollment (ACTIVE) and publish StudentEnrolled
     * (after commit). Progress Service reacts by initialising progress; if that fails it publishes
     * EnrollmentFailed and {@link EnrollmentSagaService} compensates by cancelling the enrollment.
     */
    @Transactional
    public EnrollmentResponse enroll(EnrollmentRequest request, AuthUser current) {
        Long studentId = resolveStudentId(request, current);

        CourseInfo course = courseClient.getCourse(request.courseId(), current.getToken());
        if (!"PUBLISHED".equals(course.status())) {
            throw new BadRequestException("Course is not open for enrollment");
        }
        if (enrollmentRepository.existsByStudentIdAndCourseIdAndStatusIn(
                studentId, request.courseId(), List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))) {
            throw new DuplicateResourceException("Student is already enrolled in this course");
        }
        UserInfo student = userClient.getUser(studentId, current.getToken());

        Enrollment saved = enrollmentRepository.save(Enrollment.builder()
                .studentId(studentId)
                .courseId(request.courseId())
                .status(EnrollmentStatus.ACTIVE)
                .build());
        log.info("Enrollment created: id={}, studentId={}, courseId={}", saved.getId(), studentId, saved.getCourseId());

        DomainEvent event = DomainEvent.of(EventTypes.STUDENT_ENROLLED,
                DomainEvent.aggregateId(studentId, saved.getCourseId()),
                Map.of("enrollmentId", saved.getId(),
                        "studentId", studentId,
                        "studentName", student.name(),
                        "studentEmail", student.email(),
                        "courseId", saved.getCourseId(),
                        "courseTitle", course.title()));
        eventPublisher.publish(RoutingKeys.STUDENT_ENROLLED, event);
        return EnrollmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse getById(Long id, AuthUser current) {
        Enrollment enrollment = findOrThrow(id);
        if (current.getRole() == Role.STUDENT && !enrollment.getStudentId().equals(current.getId())) {
            throw new AccessDeniedException("You can only view your own enrollments");
        }
        return EnrollmentResponse.from(enrollment);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getByStudent(Long studentId, AuthUser current) {
        if (current.getRole() == Role.STUDENT && !current.getId().equals(studentId)) {
            throw new AccessDeniedException("You can only view your own enrollments");
        }
        return enrollmentRepository.findByStudentIdOrderByEnrollmentDateDesc(studentId)
                .stream().map(EnrollmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getByCourse(Long courseId) {
        return enrollmentRepository.findByCourseIdOrderByEnrollmentDateDesc(courseId)
                .stream().map(EnrollmentResponse::from).toList();
    }

    @Transactional
    public EnrollmentResponse cancel(Long id, AuthUser current) {
        Enrollment enrollment = findOrThrow(id);
        if (current.getRole() != Role.ADMIN && !enrollment.getStudentId().equals(current.getId())) {
            throw new AccessDeniedException("You can only cancel your own enrollments");
        }
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            throw new DuplicateResourceException("Only ACTIVE enrollments can be cancelled (current: "
                    + enrollment.getStatus() + ")");
        }
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        Enrollment saved = enrollmentRepository.save(enrollment);
        log.info("Enrollment cancelled: id={} by userId={}", id, current.getId());
        return EnrollmentResponse.from(saved);
    }

    private Long resolveStudentId(EnrollmentRequest request, AuthUser current) {
        return switch (current.getRole()) {
            case STUDENT -> {
                if (request.studentId() != null && !request.studentId().equals(current.getId())) {
                    throw new AccessDeniedException("Students can only enroll themselves");
                }
                yield current.getId();
            }
            case ADMIN -> {
                if (request.studentId() == null) {
                    throw new BadRequestException("studentId is required when an admin enrolls a student");
                }
                yield request.studentId();
            }
            case INSTRUCTOR -> throw new AccessDeniedException("Instructors cannot enroll in courses");
        };
    }

    private Enrollment findOrThrow(Long id) {
        return enrollmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id " + id));
    }
}
