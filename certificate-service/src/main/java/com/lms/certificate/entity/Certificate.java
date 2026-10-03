package com.lms.certificate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Certificate of completion for one student and one course. All descriptive data (names, score, date) is
 * copied from the CourseCompleted event, so the certificate stays valid even if the source data changes later.
 */
@Entity
@Table(name = "certificates",
        uniqueConstraints = @UniqueConstraint(name = "uk_certificate_student_course",
                columnNames = {"student_id", "course_id"}),
        indexes = @Index(name = "idx_certificate_student", columnList = "student_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable number LMS-&lt;year&gt;-&lt;000123&gt;, assigned right after the first insert (derived from the id). */
    @Column(name = "certificate_number", length = 100, unique = true)
    private String certificateNumber;

    /** Reference to a user in user-service (no cross-database foreign key). */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** Reference to a course in course-service (no cross-database foreign key). */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "student_name", length = 150)
    private String studentName;

    @Column(name = "course_name", length = 200)
    private String courseName;

    @Column(name = "issue_date")
    private LocalDateTime issueDate;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    /** Random UUID used by the public verification endpoint (encoded in the QR code). */
    @Column(name = "verification_code", length = 255, unique = true)
    private String verificationCode;

    @Column(name = "certificate_url", length = 500)
    private String certificateUrl;

    @PrePersist
    void onCreate() {
        if (issueDate == null) {
            issueDate = LocalDateTime.now();
        }
    }
}
