package com.lms.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ModuleRequest(
        @Schema(example = "1")
        @NotNull(message = "courseId is required")
        @Positive(message = "courseId must be positive")
        Long courseId,

        @Schema(example = "Microservices Fundamentals")
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Schema(example = "Core concepts of microservice architecture")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Schema(example = "1")
        @NotNull(message = "sequenceNumber is required")
        @Positive(message = "sequenceNumber must be positive")
        Integer sequenceNumber
) { }
