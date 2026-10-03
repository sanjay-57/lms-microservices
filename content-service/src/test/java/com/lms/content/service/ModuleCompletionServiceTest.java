package com.lms.content.service;

import com.lms.content.client.EnrollmentClient;
import com.lms.content.dto.ModuleCompletionResponse;
import com.lms.content.entity.Module;
import com.lms.content.entity.ModuleProgress;
import com.lms.content.event.DomainEvent;
import com.lms.content.event.EventTypes;
import com.lms.content.event.RoutingKeys;
import com.lms.content.exception.DuplicateResourceException;
import com.lms.content.messaging.EventPublisher;
import com.lms.content.repository.ModuleProgressRepository;
import com.lms.content.repository.ModuleRepository;
import com.lms.content.security.AuthUser;
import com.lms.content.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleCompletionServiceTest {

    @Mock private ModuleRepository moduleRepository;
    @Mock private ModuleProgressRepository moduleProgressRepository;
    @Mock private EnrollmentClient enrollmentClient;
    @Mock private EventPublisher eventPublisher;
    @InjectMocks private ModuleCompletionService service;

    private final AuthUser student = new AuthUser(4L, "student1@example.com", Role.STUDENT, "token");
    private final Module module = Module.builder().id(7L).courseId(1L).title("Intro").sequenceNumber(1).build();

    @Test
    void complete_recordsProgressAndPublishesModuleCompleted() {
        when(moduleRepository.findById(7L)).thenReturn(Optional.of(module));
        when(moduleProgressRepository.findByStudentIdAndModuleId(4L, 7L)).thenReturn(Optional.empty());
        when(enrollmentClient.isActivelyEnrolled(4L, 1L, "token")).thenReturn(true);
        when(moduleProgressRepository.save(any(ModuleProgress.class))).thenAnswer(inv -> inv.getArgument(0));
        when(moduleProgressRepository.countByStudentIdAndCourseIdAndCompletedTrue(4L, 1L)).thenReturn(1L);
        when(moduleRepository.findByCourseIdOrderBySequenceNumberAsc(1L)).thenReturn(List.of(module, new Module(), new Module()));

        ModuleCompletionResponse response = service.complete(7L, student);

        assertThat(response.completedModules()).isEqualTo(1);
        assertThat(response.totalModules()).isEqualTo(3);
        ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eq(RoutingKeys.MODULE_COMPLETED), event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(EventTypes.MODULE_COMPLETED);
        assertThat(event.getValue().aggregateId()).isEqualTo("4-1");
        assertThat(event.getValue().longValue("totalModules")).isEqualTo(3L);
    }

    @Test
    void complete_notEnrolled_isForbidden() {
        when(moduleRepository.findById(7L)).thenReturn(Optional.of(module));
        when(moduleProgressRepository.findByStudentIdAndModuleId(4L, 7L)).thenReturn(Optional.empty());
        when(enrollmentClient.isActivelyEnrolled(4L, 1L, "token")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.complete(7L, student));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void complete_twice_isConflict() {
        when(moduleRepository.findById(7L)).thenReturn(Optional.of(module));
        when(moduleProgressRepository.findByStudentIdAndModuleId(4L, 7L)).thenReturn(Optional.of(
                ModuleProgress.builder().studentId(4L).moduleId(7L).courseId(1L).completed(true)
                        .completedAt(LocalDateTime.now()).build()));

        assertThrows(DuplicateResourceException.class, () -> service.complete(7L, student));
        verifyNoInteractions(enrollmentClient, eventPublisher);
    }
}
