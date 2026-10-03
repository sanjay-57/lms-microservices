package com.lms.content.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A module completed by a student. One row per (student, module); used to count completed modules. */
@Entity
@Table(name = "module_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_module_progress", columnNames = {"student_id", "module_id"}),
        indexes = @Index(name = "idx_module_progress_student_course", columnList = "student_id, course_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to a user in user-service (no cross-database foreign key). */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    /** Denormalised so completed modules of a course can be counted without a join. */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
