package com.lms.content.service;

import com.lms.content.client.EnrollmentClient;
import com.lms.content.dto.ModuleCompletionResponse;
import com.lms.content.entity.Module;
import com.lms.content.entity.ModuleProgress;
import com.lms.content.event.DomainEvent;
import com.lms.content.event.EventTypes;
import com.lms.content.event.RoutingKeys;
import com.lms.content.exception.DuplicateResourceException;
import com.lms.content.exception.ResourceNotFoundException;
import com.lms.content.messaging.EventPublisher;
import com.lms.content.repository.ModuleProgressRepository;
import com.lms.content.repository.ModuleRepository;
import com.lms.content.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Student completes a module": records module_progress and publishes ModuleCompleted (after commit),
 * which progress-service consumes to update the course percentage.
 */
@Service
@RequiredArgsConstructor
public class ModuleCompletionService {

    private static final Logger log = LoggerFactory.getLogger(ModuleCompletionService.class);

    private final ModuleRepository moduleRepository;
    private final ModuleProgressRepository moduleProgressRepository;
    private final EnrollmentClient enrollmentClient;
    private final EventPublisher eventPublisher;

    @Transactional
    public ModuleCompletionResponse complete(Long moduleId, AuthUser current) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with id " + moduleId));
        Long studentId = current.getId();
        Long courseId = module.getCourseId();

        if (moduleProgressRepository.findByStudentIdAndModuleId(studentId, moduleId)
                .filter(ModuleProgress::isCompleted).isPresent()) {
            throw new DuplicateResourceException("Module " + moduleId + " is already completed");
        }
        if (!enrollmentClient.isActivelyEnrolled(studentId, courseId, current.getToken())) {
            throw new AccessDeniedException("You must have an active enrollment in course " + courseId);
        }

        ModuleProgress saved = moduleProgressRepository.save(ModuleProgress.builder()
                .studentId(studentId)
                .moduleId(moduleId)
                .courseId(courseId)
                .completed(true)
                .completedAt(LocalDateTime.now())
                .build());
        long completed = moduleProgressRepository.countByStudentIdAndCourseIdAndCompletedTrue(studentId, courseId);
        long total = moduleRepository.findByCourseIdOrderBySequenceNumberAsc(courseId).size();
        log.info("Module completed: moduleId={}, courseId={}, studentId={} ({}/{})",
                moduleId, courseId, studentId, completed, total);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("moduleId", moduleId);
        payload.put("moduleTitle", module.getTitle());
        payload.put("courseId", courseId);
        payload.put("studentId", studentId);
        payload.put("completedModules", completed);
        payload.put("totalModules", total);
        eventPublisher.publish(RoutingKeys.MODULE_COMPLETED, DomainEvent.of(EventTypes.MODULE_COMPLETED,
                DomainEvent.aggregateId(studentId, courseId), payload));

        return new ModuleCompletionResponse(moduleId, courseId, studentId, completed, total, saved.getCompletedAt());
    }

    @Transactional(readOnly = true)
    public List<Long> completedModuleIds(Long courseId, AuthUser current) {
        return moduleProgressRepository.findByStudentIdAndCourseIdAndCompletedTrue(current.getId(), courseId)
                .stream().map(ModuleProgress::getModuleId).toList();
    }
}
