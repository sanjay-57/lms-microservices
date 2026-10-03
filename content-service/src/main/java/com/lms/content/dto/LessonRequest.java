package com.lms.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LessonRequest(
        @Schema(example = "1")
        @NotNull(message = "moduleId is required")
        @Positive(message = "moduleId must be positive")
        Long moduleId,

        @Schema(example = "What are microservices?")
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Schema(example = "Introduction lesson")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Schema(example = "https://www.w3schools.com/html/mov_bbb.mp4")
        @Size(max = 500, message = "videoUrl must be at most 500 characters")
        String videoUrl,

        @Schema(example = "https://example.com/docs/intro.pdf")
        @Size(max = 500, message = "documentUrl must be at most 500 characters")
        String documentUrl,

        @Schema(example = "15")
        @PositiveOrZero(message = "durationMinutes must not be negative")
        Integer durationMinutes
) { }
