package com.lms.progress.controller;

import com.lms.progress.dto.EventResponse;
import com.lms.progress.dto.ProgressResponse;
import com.lms.progress.dto.RebuildResponse;
import com.lms.progress.security.AuthUser;
import com.lms.progress.service.EventStoreService;
import com.lms.progress.service.ProgressQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Read-only API (progress is changed only by events) plus event-store inspection and read-model rebuild. */
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Progress", description = "Learning progress (CQRS read model) and its event-sourced history")
public class ProgressController {

    private final ProgressQueryService queryService;
    private final EventStoreService eventStoreService;

    @Operation(summary = "List a student's progress in all courses (self, INSTRUCTOR or ADMIN)")
    @GetMapping("/student/{studentId}")
    public List<ProgressResponse> byStudent(@PathVariable Long studentId, @AuthenticationPrincipal AuthUser current) {
        return queryService.getByStudent(studentId, current);
    }

    @Operation(summary = "Progress of a student in one course (self, INSTRUCTOR or ADMIN)")
    @ApiResponse(responseCode = "404", description = "No progress for this student/course")
    @GetMapping("/student/{studentId}/course/{courseId}")
    public ProgressResponse byStudentAndCourse(@PathVariable Long studentId, @PathVariable Long courseId,
                                               @AuthenticationPrincipal AuthUser current) {
        return queryService.getByStudentAndCourse(studentId, courseId, current);
    }

    @Operation(summary = "Progress of all students of a course (INSTRUCTOR or ADMIN)")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @GetMapping("/course/{courseId}")
    public List<ProgressResponse> byCourse(@PathVariable Long courseId) {
        return queryService.getByCourse(courseId);
    }

    @Operation(summary = "Event-sourced history of a progress aggregate '<studentId>-<courseId>' (students: own only)")
    @ApiResponse(responseCode = "404", description = "No events for this aggregate")
    @GetMapping("/events/{aggregateId}")
    public List<EventResponse> history(@PathVariable String aggregateId, @AuthenticationPrincipal AuthUser current) {
        return eventStoreService.getHistory(aggregateId, current);
    }

    @Operation(summary = "Drop the read model and rebuild it by replaying the whole event store (ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/rebuild")
    public RebuildResponse rebuild() {
        return eventStoreService.rebuildReadModel();
    }
}
