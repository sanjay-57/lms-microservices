package com.lms.certificate.dto;

import com.lms.certificate.entity.Certificate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Public view of a certificate: only what a third party needs to confirm it is genuine. */
public record CertificateVerificationResponse(boolean valid, String certificateNumber, String studentName,
                                              String courseName, LocalDateTime issueDate,
                                              LocalDateTime completionDate, BigDecimal finalScore) {

    public static CertificateVerificationResponse from(Certificate c) {
        return new CertificateVerificationResponse(true, c.getCertificateNumber(), c.getStudentName(),
                c.getCourseName(), c.getIssueDate(), c.getCompletionDate(), c.getFinalScore());
    }
}
