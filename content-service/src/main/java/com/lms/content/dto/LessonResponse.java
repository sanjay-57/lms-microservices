package com.lms.content.dto;

import com.lms.content.entity.Lesson;

public record LessonResponse(Long id, Long moduleId, String title, String description,
                             String videoUrl, String documentUrl, Integer durationMinutes) {

    public static LessonResponse from(Lesson l) {
        return new LessonResponse(l.getId(), l.getModuleId(), l.getTitle(), l.getDescription(),
                l.getVideoUrl(), l.getDocumentUrl(), l.getDurationMinutes());
    }
}
