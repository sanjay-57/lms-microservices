package com.lms.content.service;

import com.lms.content.dto.ModuleRequest;
import com.lms.content.dto.ModuleResponse;
import com.lms.content.entity.Module;
import com.lms.content.exception.DuplicateResourceException;
import com.lms.content.exception.ResourceNotFoundException;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.ModuleProgressRepository;
import com.lms.content.repository.ModuleRepository;
import com.lms.content.security.AuthUser;
import com.lms.content.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleServiceTest {

    @Mock private ModuleRepository moduleRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private ModuleProgressRepository moduleProgressRepository;
    @Mock private CourseAccessService courseAccessService;
    @InjectMocks private ModuleService moduleService;

    private final AuthUser instructor = new AuthUser(2L, "instructor@example.com", Role.INSTRUCTOR, "token");

    @Test
    void create_success() {
        when(moduleRepository.existsByCourseIdAndSequenceNumber(1L, 1)).thenReturn(false);
        when(moduleRepository.save(any(Module.class))).thenAnswer(inv -> {
            Module m = inv.getArgument(0);
            m.setId(11L);
            return m;
        });

        ModuleResponse response = moduleService.create(new ModuleRequest(1L, "Intro", "d", 1), instructor);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.courseId()).isEqualTo(1L);
        verify(courseAccessService).assertCanManage(1L, instructor);
    }

    @Test
    void create_duplicateSequenceNumber_isConflict() {
        when(moduleRepository.existsByCourseIdAndSequenceNumber(1L, 1)).thenReturn(true);
        assertThrows(DuplicateResourceException.class,
                () -> moduleService.create(new ModuleRequest(1L, "Intro", "d", 1), instructor));
        verify(moduleRepository, never()).save(any());
    }

    @Test
    void create_forCourseOfAnotherInstructor_isForbidden() {
        doThrow(new AccessDeniedException("not yours"))
                .when(courseAccessService).assertCanManage(eq(1L), any(AuthUser.class));
        assertThrows(AccessDeniedException.class,
                () -> moduleService.create(new ModuleRequest(1L, "Intro", "d", 1), instructor));
        verify(moduleRepository, never()).save(any());
    }

    @Test
    void delete_removesLessonsThenModule() {
        Module module = Module.builder().id(5L).courseId(1L).title("M").sequenceNumber(1).build();
        when(moduleRepository.findById(5L)).thenReturn(Optional.of(module));

        moduleService.delete(5L, instructor);

        verify(lessonRepository).deleteByModuleId(5L);
        verify(moduleRepository).delete(module);
    }

    @Test
    void getById_unknown_throwsNotFound() {
        when(moduleRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> moduleService.getById(99L));
    }
}
