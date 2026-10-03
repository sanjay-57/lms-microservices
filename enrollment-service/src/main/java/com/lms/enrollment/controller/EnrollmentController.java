package com.lms.enrollment.controller;

import com.lms.enrollment.dto.EnrollmentRequest;
import com.lms.enrollment.dto.EnrollmentResponse;
import com.lms.enrollment.security.AuthUser;
import com.lms.enrollment.service.EnrollmentService;
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
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Enrollments", description = "Course enrollment (publishes StudentEnrolled)")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @Operation(summary = "Enroll in a published course; publishes the StudentEnrolled event")
    @ApiResponse(responseCode = "201", description = "Enrolled")
    @ApiResponse(responseCode = "404", description = "Course not found / not published")
    @ApiResponse(responseCode = "409", description = "Already enrolled")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    @PostMapping
    public ResponseEntity<EnrollmentResponse> enroll(@Valid @RequestBody EnrollmentRequest request,
                                                     @AuthenticationPrincipal AuthUser current) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.enroll(request, current));
    }

    @Operation(summary = "List a student's enrollments (self, INSTRUCTOR or ADMIN)")
    @GetMapping("/student/{studentId}")
    public List<EnrollmentResponse> byStudent(@PathVariable Long studentId, @AuthenticationPrincipal AuthUser current) {
        return enrollmentService.getByStudent(studentId, current);
    }

    @Operation(summary = "List enrollments of a course (INSTRUCTOR or ADMIN)")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @GetMapping("/course/{courseId}")
    public List<EnrollmentResponse> byCourse(@PathVariable Long courseId) {
        return enrollmentService.getByCourse(courseId);
    }

    @Operation(summary = "Get an enrollment by id")
    @GetMapping("/{id}")
    public EnrollmentResponse getById(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        return enrollmentService.getById(id, current);
    }

    @Operation(summary = "Cancel an ACTIVE enrollment (owner or ADMIN)")
    @PutMapping("/{id}/cancel")
    public EnrollmentResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        return enrollmentService.cancel(id, current);
    }
}
