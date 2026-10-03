package com.lms.course.dto;

import com.lms.course.entity.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CourseRequest(
        @Schema(example = "Java Spring Boot Microservices")
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Schema(example = "Build production-style microservices with Spring Boot and RabbitMQ.")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Schema(example = "Programming")
        @NotBlank(message = "Category is required")
        @Size(max = 100, message = "Category must be at most 100 characters")
        String category,

        @Schema(example = "2", description = "Only honoured for ADMIN; instructors always own their courses")
        Long instructorId,

        @Schema(example = "40")
        @NotNull(message = "Duration is required")
        @Positive(message = "Duration must be positive")
        Integer durationHours,

        @Schema(example = "4999.00")
        @NotNull(message = "Price is required")
        @PositiveOrZero(message = "Price must not be negative")
        BigDecimal price,

        @Schema(example = "PUBLISHED", description = "DRAFT (default), PUBLISHED or ARCHIVED")
        CourseStatus status
) { }
