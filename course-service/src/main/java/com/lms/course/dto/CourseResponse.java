package com.lms.course.dto;

import com.lms.course.entity.Course;
import com.lms.course.entity.CourseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CourseResponse(
        Long id,
        String title,
        String description,
        String category,
        Long instructorId,
        Integer durationHours,
        BigDecimal price,
        CourseStatus status,
        LocalDateTime createdAt
) {
    public static CourseResponse from(Course c) {
        return new CourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getCategory(),
                c.getInstructorId(), c.getDurationHours(), c.getPrice(), c.getStatus(), c.getCreatedAt());
    }
}
