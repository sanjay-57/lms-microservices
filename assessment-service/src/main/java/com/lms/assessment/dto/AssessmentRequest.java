package com.lms.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AssessmentRequest(
        @Schema(example = "1")
        @NotNull(message = "courseId is required")
        @Positive(message = "courseId must be positive")
        Long courseId,

        @Schema(example = "Final Assessment")
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Schema(example = "60.00", description = "Pass mark as a percentage (0-100)")
        @NotNull(message = "passMark is required")
        @DecimalMin(value = "0.00", message = "passMark must be between 0 and 100")
        @DecimalMax(value = "100.00", message = "passMark must be between 0 and 100")
        BigDecimal passMark
) { }
