package com.lms.enrollment.dto;

import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.entity.EnrollmentStatus;

import java.time.LocalDateTime;

public record EnrollmentResponse(Long id, Long studentId, Long courseId,
                                 LocalDateTime enrollmentDate, EnrollmentStatus status) {

    public static EnrollmentResponse from(Enrollment e) {
        return new EnrollmentResponse(e.getId(), e.getStudentId(), e.getCourseId(), e.getEnrollmentDate(), e.getStatus());
    }
}
