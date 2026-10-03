package com.lms.assessment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record SubmitRequest(
        @Schema(example = "{\"1\": \"A\", \"2\": \"C\", \"3\": \"B\", \"4\": \"D\", \"5\": \"A\"}",
                description = "Map of questionId -> chosen option (A, B, C or D)")
        @NotEmpty(message = "answers must not be empty")
        Map<Long, String> answers
) { }
