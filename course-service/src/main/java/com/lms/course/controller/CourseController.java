package com.lms.course.controller;

import com.lms.course.dto.CourseRequest;
import com.lms.course.dto.CourseResponse;
import com.lms.course.security.AuthUser;
import com.lms.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Courses", description = "Course creation and browsing")
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "Create a course (INSTRUCTOR or ADMIN)")
    @ApiResponse(responseCode = "201", description = "Course created")
    @ApiResponse(responseCode = "403", description = "Students cannot create courses")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseRequest request,
                                                 @AuthenticationPrincipal AuthUser current) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.create(request, current));
    }

    @Operation(summary = "List courses visible to the caller (students see PUBLISHED only)")
    @GetMapping
    public List<CourseResponse> list(@AuthenticationPrincipal AuthUser current) {
        return courseService.list(current);
    }

    @Operation(summary = "Get a course by id")
    @ApiResponse(responseCode = "404", description = "Course not found or not visible")
    @GetMapping("/{id}")
    public CourseResponse getById(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        return courseService.getById(id, current);
    }

    @Operation(summary = "List courses of an instructor")
    @GetMapping("/instructor/{instructorId}")
    public List<CourseResponse> getByInstructor(@PathVariable Long instructorId,
                                                @AuthenticationPrincipal AuthUser current) {
        return courseService.getByInstructor(instructorId, current);
    }

    @Operation(summary = "Update a course (owner instructor or ADMIN)")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PutMapping("/{id}")
    public CourseResponse update(@PathVariable Long id,
                                 @Valid @RequestBody CourseRequest request,
                                 @AuthenticationPrincipal AuthUser current) {
        return courseService.update(id, request, current);
    }

    @Operation(summary = "Delete a course (owner instructor or ADMIN)")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        courseService.delete(id, current);
        return ResponseEntity.noContent().build();
    }
}
