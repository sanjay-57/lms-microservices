package com.lms.progress.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read model (query side of CQRS): a denormalised, query-friendly projection of one CourseProgress
 * aggregate. It is only ever written by {@code ProgressProjector} and can be dropped and rebuilt from the
 * event store at any time.
 */
@Entity
@Table(name = "progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_progress_student_course", columnNames = {"student_id", "course_id"}),
        indexes = {
                @Index(name = "idx_progress_student", columnList = "student_id"),
                @Index(name = "idx_progress_course", columnList = "course_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Progress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "enrollment_id")
    private Long enrollmentId;

    @Column(name = "student_name", length = 150)
    private String studentName;

    @Column(name = "student_email", length = 150)
    private String studentEmail;

    @Column(name = "course_title", length = 200)
    private String courseTitle;

    @Builder.Default
    @Column(name = "completed_modules", nullable = false)
    private Integer completedModules = 0;

    @Builder.Default
    @Column(name = "total_modules", nullable = false)
    private Integer totalModules = 0;

    @Builder.Default
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "assessment_passed", nullable = false)
    private Boolean assessmentPassed = false;

    @Column(name = "best_score", precision = 5, scale = 2)
    private BigDecimal bestScore;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProgressStatus status = ProgressStatus.NOT_STARTED;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
