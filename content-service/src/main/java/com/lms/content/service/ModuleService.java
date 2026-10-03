package com.lms.content.service;

import com.lms.content.dto.ModuleRequest;
import com.lms.content.dto.ModuleResponse;
import com.lms.content.entity.Module;
import com.lms.content.exception.BadRequestException;
import com.lms.content.exception.DuplicateResourceException;
import com.lms.content.exception.ResourceNotFoundException;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.ModuleProgressRepository;
import com.lms.content.repository.ModuleRepository;
import com.lms.content.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private static final Logger log = LoggerFactory.getLogger(ModuleService.class);

    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final ModuleProgressRepository moduleProgressRepository;
    private final CourseAccessService courseAccessService;

    @Transactional
    public ModuleResponse create(ModuleRequest request, AuthUser current) {
        courseAccessService.assertCanManage(request.courseId(), current);
        if (moduleRepository.existsByCourseIdAndSequenceNumber(request.courseId(), request.sequenceNumber())) {
            throw new DuplicateResourceException("Course " + request.courseId()
                    + " already has a module with sequence number " + request.sequenceNumber());
        }
        Module saved = moduleRepository.save(Module.builder()
                .courseId(request.courseId())
                .title(request.title().trim())
                .description(request.description())
                .sequenceNumber(request.sequenceNumber())
                .build());
        log.info("Module created: id={}, courseId={}, seq={}", saved.getId(), saved.getCourseId(), saved.getSequenceNumber());
        return ModuleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ModuleResponse> getByCourse(Long courseId) {
        return moduleRepository.findByCourseIdOrderBySequenceNumberAsc(courseId)
                .stream().map(ModuleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ModuleResponse getById(Long moduleId) {
        return ModuleResponse.from(findOrThrow(moduleId));
    }

    @Transactional
    public ModuleResponse update(Long id, ModuleRequest request, AuthUser current) {
        Module module = findOrThrow(id);
        if (!module.getCourseId().equals(request.courseId())) {
            throw new BadRequestException("A module cannot be moved to a different course");
        }
        courseAccessService.assertCanManage(module.getCourseId(), current);
        if (!module.getSequenceNumber().equals(request.sequenceNumber())
                && moduleRepository.existsByCourseIdAndSequenceNumber(module.getCourseId(), request.sequenceNumber())) {
            throw new DuplicateResourceException("Course " + module.getCourseId()
                    + " already has a module with sequence number " + request.sequenceNumber());
        }
        module.setTitle(request.title().trim());
        module.setDescription(request.description());
        module.setSequenceNumber(request.sequenceNumber());
        Module saved = moduleRepository.save(module);
        log.info("Module updated: id={} by userId={}", id, current.getId());
        return ModuleResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, AuthUser current) {
        Module module = findOrThrow(id);
        courseAccessService.assertCanManage(module.getCourseId(), current);
        lessonRepository.deleteByModuleId(id);
        moduleProgressRepository.deleteByModuleId(id);
        moduleRepository.delete(module);
        log.info("Module deleted (with its lessons): id={} by userId={}", id, current.getId());
    }

    private Module findOrThrow(Long id) {
        return moduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with id " + id));
    }
}
