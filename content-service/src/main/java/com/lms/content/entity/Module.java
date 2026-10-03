package com.lms.content.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "modules", indexes = @Index(name = "idx_module_course", columnList = "course_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to a course in course-service (no cross-database foreign key). */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;
}
