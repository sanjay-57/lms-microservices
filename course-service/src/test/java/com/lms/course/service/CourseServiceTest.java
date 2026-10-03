package com.lms.course.service;

import com.lms.course.dto.CourseRequest;
import com.lms.course.dto.CourseResponse;
import com.lms.course.entity.Course;
import com.lms.course.entity.CourseStatus;
import com.lms.course.exception.ResourceNotFoundException;
import com.lms.course.repository.CourseRepository;
import com.lms.course.security.AuthUser;
import com.lms.course.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;
    @InjectMocks private CourseService courseService;

    private final AuthUser instructor = new AuthUser(2L, "instructor@example.com", Role.INSTRUCTOR);
    private final AuthUser otherInstructor = new AuthUser(3L, "instructor2@example.com", Role.INSTRUCTOR);
    private final AuthUser student = new AuthUser(4L, "student1@example.com", Role.STUDENT);
    private final AuthUser admin = new AuthUser(1L, "admin@example.com", Role.ADMIN);

    private Course course(Long id, Long instructorId, CourseStatus status) {
        return Course.builder().id(id).title("Java").description("d").category("Programming")
                .instructorId(instructorId).durationHours(40).price(new BigDecimal("999.00")).status(status).build();
    }

    private CourseRequest request(CourseStatus status) {
        return new CourseRequest("Java", "desc", "Programming", null, 40, new BigDecimal("999.00"), status);
    }

    @Test
    void create_asInstructor_usesTokenIdentityAndDefaultsToDraft() {
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        CourseResponse response = courseService.create(request(null), instructor);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.instructorId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    void create_asAdmin_canAssignInstructor() {
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        CourseRequest req = new CourseRequest("Java", "d", "Programming", 3L, 40,
                new BigDecimal("10"), CourseStatus.PUBLISHED);

        CourseResponse response = courseService.create(req, admin);

        assertThat(response.instructorId()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(CourseStatus.PUBLISHED);
    }

    @Test
    void list_asStudent_returnsOnlyPublished() {
        when(courseRepository.findByStatusOrderByCreatedAtDesc(CourseStatus.PUBLISHED))
                .thenReturn(List.of(course(1L, 2L, CourseStatus.PUBLISHED)));

        List<CourseResponse> result = courseService.list(student);

        assertThat(result).hasSize(1);
        verify(courseRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void getById_draftCourse_isHiddenFromStudent() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course(1L, 2L, CourseStatus.DRAFT)));
        assertThrows(ResourceNotFoundException.class, () -> courseService.getById(1L, student));
    }

    @Test
    void getById_draftCourse_isVisibleToOwnerAndAdmin() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course(1L, 2L, CourseStatus.DRAFT)));
        assertThat(courseService.getById(1L, instructor).id()).isEqualTo(1L);
        assertThat(courseService.getById(1L, admin).id()).isEqualTo(1L);
    }

    @Test
    void update_byOtherInstructor_isForbidden() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course(1L, 2L, CourseStatus.PUBLISHED)));
        assertThrows(AccessDeniedException.class,
                () -> courseService.update(1L, request(CourseStatus.PUBLISHED), otherInstructor));
        verify(courseRepository, never()).save(any());
    }

    @Test
    void update_byOwner_changesFields() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course(1L, 2L, CourseStatus.DRAFT)));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        CourseRequest req = new CourseRequest("New Title", "new", "Web", null, 12,
                new BigDecimal("50.00"), CourseStatus.PUBLISHED);

        CourseResponse response = courseService.update(1L, req, instructor);

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.durationHours()).isEqualTo(12);
        assertThat(response.status()).isEqualTo(CourseStatus.PUBLISHED);
    }

    @Test
    void delete_byAdmin_removesCourse() {
        Course c = course(1L, 2L, CourseStatus.PUBLISHED);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));

        courseService.delete(1L, admin);

        verify(courseRepository).delete(c);
    }

    @Test
    void delete_unknownCourse_throwsNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> courseService.delete(99L, admin));
    }
}
