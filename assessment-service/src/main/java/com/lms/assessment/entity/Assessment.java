package com.lms.assessment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "assessments", indexes = @Index(name = "idx_assessment_course", columnList = "course_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to a course in course-service (no cross-database foreign key). */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 200)
    private String title;

    /** Minimum percentage (0-100) required to pass. */
    @Column(name = "pass_mark", nullable = false, precision = 5, scale = 2)
    private BigDecimal passMark;

    /** Sum of the marks of all questions; maintained when questions are added. */
    @Column(name = "total_marks", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalMarks;
}
