package com.lms.course.service;

import com.lms.course.dto.CourseRequest;
import com.lms.course.dto.CourseResponse;
import com.lms.course.entity.Course;
import com.lms.course.entity.CourseStatus;
import com.lms.course.exception.ResourceNotFoundException;
import com.lms.course.repository.CourseRepository;
import com.lms.course.security.AuthUser;
import com.lms.course.security.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;

    @Transactional
    public CourseResponse create(CourseRequest request, AuthUser current) {
        Long instructorId = (current.getRole() == Role.ADMIN && request.instructorId() != null)
                ? request.instructorId()
                : current.getId();
        Course course = Course.builder()
                .title(request.title().trim())
                .description(request.description())
                .category(request.category().trim())
                .instructorId(instructorId)
                .durationHours(request.durationHours())
                .price(request.price())
                .status(request.status() == null ? CourseStatus.DRAFT : request.status())
                .build();
        Course saved = courseRepository.save(course);
        log.info("Course created: id={}, title='{}', instructorId={}, status={}",
                saved.getId(), saved.getTitle(), saved.getInstructorId(), saved.getStatus());
        return CourseResponse.from(saved);
    }

    /** Students see PUBLISHED only; instructors see PUBLISHED plus their own; admins see everything. */
    @Transactional(readOnly = true)
    public List<CourseResponse> list(AuthUser current) {
        List<Course> courses = switch (current.getRole()) {
            case ADMIN -> courseRepository.findAllByOrderByCreatedAtDesc();
            case INSTRUCTOR -> courseRepository
                    .findByStatusOrInstructorIdOrderByCreatedAtDesc(CourseStatus.PUBLISHED, current.getId());
            case STUDENT -> courseRepository.findByStatusOrderByCreatedAtDesc(CourseStatus.PUBLISHED);
        };
        return courses.stream().map(CourseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse getById(Long id, AuthUser current) {
        Course course = findOrThrow(id);
        if (!canView(course, current)) {
            // Hide the existence of unpublished courses from users who may not see them
            throw new ResourceNotFoundException("Course not found with id " + id);
        }
        return CourseResponse.from(course);
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getByInstructor(Long instructorId, AuthUser current) {
        boolean seesAll = current.getRole() == Role.ADMIN
                || (current.getRole() == Role.INSTRUCTOR && current.getId().equals(instructorId));
        List<Course> courses = seesAll
                ? courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId)
                : courseRepository.findByInstructorIdAndStatusOrderByCreatedAtDesc(instructorId, CourseStatus.PUBLISHED);
        return courses.stream().map(CourseResponse::from).toList();
    }

    @Transactional
    public CourseResponse update(Long id, CourseRequest request, AuthUser current) {
        Course course = findOrThrow(id);
        assertCanModify(course, current);

        course.setTitle(request.title().trim());
        course.setDescription(request.description());
        course.setCategory(request.category().trim());
        course.setDurationHours(request.durationHours());
        course.setPrice(request.price());
        if (request.status() != null) {
            course.setStatus(request.status());
        }
        if (current.getRole() == Role.ADMIN && request.instructorId() != null) {
            course.setInstructorId(request.instructorId());
        }
        Course saved = courseRepository.save(course);
        log.info("Course updated: id={} by userId={}", saved.getId(), current.getId());
        return CourseResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, AuthUser current) {
        Course course = findOrThrow(id);
        assertCanModify(course, current);
        courseRepository.delete(course);
        log.info("Course deleted: id={} by userId={}", id, current.getId());
    }

    private boolean canView(Course course, AuthUser current) {
        if (course.getStatus() == CourseStatus.PUBLISHED || current.getRole() == Role.ADMIN) {
            return true;
        }
        return current.getRole() == Role.INSTRUCTOR && course.getInstructorId().equals(current.getId());
    }

    private void assertCanModify(Course course, AuthUser current) {
        boolean owner = current.getRole() == Role.INSTRUCTOR && course.getInstructorId().equals(current.getId());
        if (current.getRole() != Role.ADMIN && !owner) {
            throw new AccessDeniedException("You can only modify your own courses");
        }
    }

    private Course findOrThrow(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id " + id));
    }
}
