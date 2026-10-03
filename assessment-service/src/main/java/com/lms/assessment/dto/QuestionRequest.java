package com.lms.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @Schema(example = "Which annotation marks a Spring Boot application entry point?")
        @NotBlank(message = "questionText is required")
        @Size(max = 2000, message = "questionText must be at most 2000 characters")
        String questionText,

        @Schema(example = "@SpringBootApplication")
        @NotBlank(message = "optionA is required")
        @Size(max = 500, message = "optionA must be at most 500 characters")
        String optionA,

        @Schema(example = "@EnableWebApp")
        @NotBlank(message = "optionB is required")
        @Size(max = 500, message = "optionB must be at most 500 characters")
        String optionB,

        @Schema(example = "@RestApplication")
        @NotBlank(message = "optionC is required")
        @Size(max = 500, message = "optionC must be at most 500 characters")
        String optionC,

        @Schema(example = "@BootMain")
        @NotBlank(message = "optionD is required")
        @Size(max = 500, message = "optionD must be at most 500 characters")
        String optionD,

        @Schema(example = "A")
        @NotBlank(message = "correctOption is required")
        @Pattern(regexp = "^[A-Da-d]$", message = "correctOption must be one of A, B, C, D")
        String correctOption,

        @Schema(example = "20")
        @NotNull(message = "marks is required")
        @Positive(message = "marks must be positive")
        Integer marks
) { }
