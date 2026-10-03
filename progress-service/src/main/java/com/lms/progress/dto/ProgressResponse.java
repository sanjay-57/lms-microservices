package com.lms.progress.dto;

import com.lms.progress.entity.Progress;
import com.lms.progress.entity.ProgressStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProgressResponse(Long id, Long studentId, Long courseId, Long enrollmentId,
                               String studentName, String studentEmail, String courseTitle,
                               Integer completedModules, Integer totalModules, BigDecimal percentage,
                               Boolean assessmentPassed, BigDecimal bestScore, ProgressStatus status,
                               LocalDateTime updatedAt, LocalDateTime completedAt) {

    public static ProgressResponse from(Progress p) {
        return new ProgressResponse(p.getId(), p.getStudentId(), p.getCourseId(), p.getEnrollmentId(),
                p.getStudentName(), p.getStudentEmail(), p.getCourseTitle(),
                p.getCompletedModules(), p.getTotalModules(), p.getPercentage(),
                p.getAssessmentPassed(), p.getBestScore(), p.getStatus(),
                p.getUpdatedAt(), p.getCompletedAt());
    }
}
