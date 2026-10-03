package com.lms.content.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lessons", indexes = @Index(name = "idx_lesson_module", columnList = "module_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;
}
