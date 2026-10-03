package com.lms.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EnrollmentRequest(
        @Schema(example = "4", description = "Ignored for STUDENT (token identity is used). Required for ADMIN.")
        Long studentId,

        @Schema(example = "1")
        @NotNull(message = "courseId is required")
        @Positive(message = "courseId must be positive")
        Long courseId
) { }
