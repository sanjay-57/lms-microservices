package com.lms.assessment.dto;

import com.lms.assessment.entity.Assessment;

import java.math.BigDecimal;

public record AssessmentResponse(Long id, Long courseId, String title, BigDecimal passMark, BigDecimal totalMarks) {

    public static AssessmentResponse from(Assessment a) {
        return new AssessmentResponse(a.getId(), a.getCourseId(), a.getTitle(), a.getPassMark(), a.getTotalMarks());
    }
}
