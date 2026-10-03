package com.lms.assessment.dto;

import com.lms.assessment.entity.Submission;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubmissionResponse(Long id, Long assessmentId, Long studentId, BigDecimal score,
                                 BigDecimal totalMarks, BigDecimal percentage, boolean passed,
                                 LocalDateTime submittedAt) {

    public static SubmissionResponse from(Submission s, BigDecimal totalMarks) {
        return new SubmissionResponse(s.getId(), s.getAssessmentId(), s.getStudentId(), s.getScore(),
                totalMarks, s.getPercentage(), s.isPassed(), s.getSubmittedAt());
    }
}
