package com.lms.content.dto;

import com.lms.content.entity.Module;

public record ModuleResponse(Long id, Long courseId, String title, String description, Integer sequenceNumber) {

    public static ModuleResponse from(Module m) {
        return new ModuleResponse(m.getId(), m.getCourseId(), m.getTitle(), m.getDescription(), m.getSequenceNumber());
    }
}
