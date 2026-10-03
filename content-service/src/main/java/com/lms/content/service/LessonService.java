package com.lms.content.service;

import com.lms.content.dto.LessonRequest;
import com.lms.content.dto.LessonResponse;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.Module;
import com.lms.content.exception.BadRequestException;
import com.lms.content.exception.ResourceNotFoundException;
import com.lms.content.repository.LessonRepository;
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
public class LessonService {

    private static final Logger log = LoggerFactory.getLogger(LessonService.class);

    private final LessonRepository lessonRepository;
    private final ModuleRepository moduleRepository;
    private final CourseAccessService courseAccessService;

    @Transactional
    public LessonResponse create(LessonRequest request, AuthUser current) {
        Module module = moduleOrThrow(request.moduleId());
        courseAccessService.assertCanManage(module.getCourseId(), current);
        Lesson saved = lessonRepository.save(Lesson.builder()
                .moduleId(module.getId())
                .title(request.title().trim())
                .description(request.description())
                .videoUrl(request.videoUrl())
                .documentUrl(request.documentUrl())
                .durationMinutes(request.durationMinutes())
                .build());
        log.info("Lesson created: id={}, moduleId={}", saved.getId(), saved.getModuleId());
        return LessonResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> getByModule(Long moduleId) {
        moduleOrThrow(moduleId);
        return lessonRepository.findByModuleIdOrderByIdAsc(moduleId).stream().map(LessonResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LessonResponse getById(Long lessonId) {
        return LessonResponse.from(lessonOrThrow(lessonId));
    }

    @Transactional
    public LessonResponse update(Long id, LessonRequest request, AuthUser current) {
        Lesson lesson = lessonOrThrow(id);
        if (!lesson.getModuleId().equals(request.moduleId())) {
            throw new BadRequestException("A lesson cannot be moved to a different module");
        }
        Module module = moduleOrThrow(lesson.getModuleId());
        courseAccessService.assertCanManage(module.getCourseId(), current);
        lesson.setTitle(request.title().trim());
        lesson.setDescription(request.description());
        lesson.setVideoUrl(request.videoUrl());
        lesson.setDocumentUrl(request.documentUrl());
        lesson.setDurationMinutes(request.durationMinutes());
        log.info("Lesson updated: id={} by userId={}", id, current.getId());
        return LessonResponse.from(lessonRepository.save(lesson));
    }

    @Transactional
    public void delete(Long id, AuthUser current) {
        Lesson lesson = lessonOrThrow(id);
        Module module = moduleOrThrow(lesson.getModuleId());
        courseAccessService.assertCanManage(module.getCourseId(), current);
        lessonRepository.delete(lesson);
        log.info("Lesson deleted: id={} by userId={}", id, current.getId());
    }

    private Module moduleOrThrow(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with id " + moduleId));
    }

    private Lesson lessonOrThrow(Long id) {
        return lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id " + id));
    }
}
