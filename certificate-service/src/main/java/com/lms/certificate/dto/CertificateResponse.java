package com.lms.certificate.dto;

import com.lms.certificate.entity.Certificate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CertificateResponse(Long id, String certificateNumber, Long studentId, Long courseId,
                                  String studentName, String courseName, LocalDateTime issueDate,
                                  LocalDateTime completionDate, BigDecimal finalScore,
                                  String verificationCode, String certificateUrl) {

    public static CertificateResponse from(Certificate c) {
        return new CertificateResponse(c.getId(), c.getCertificateNumber(), c.getStudentId(), c.getCourseId(),
                c.getStudentName(), c.getCourseName(), c.getIssueDate(), c.getCompletionDate(), c.getFinalScore(),
                c.getVerificationCode(), c.getCertificateUrl());
    }
}
