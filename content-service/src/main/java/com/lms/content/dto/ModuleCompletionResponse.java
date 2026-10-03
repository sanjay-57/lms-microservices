package com.lms.content.dto;

import java.time.LocalDateTime;

public record ModuleCompletionResponse(Long moduleId, Long courseId, Long studentId,
                                       long completedModules, long totalModules, LocalDateTime completedAt) { }
