package com.lms.content.controller;

import com.lms.content.dto.LessonRequest;
import com.lms.content.dto.LessonResponse;
import com.lms.content.security.AuthUser;
import com.lms.content.service.LessonService;
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
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Lessons", description = "Lessons inside modules")
public class LessonController {

    private final LessonService lessonService;

    @Operation(summary = "Create a lesson (owner INSTRUCTOR or ADMIN)")
    @ApiResponse(responseCode = "201", description = "Lesson created")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PostMapping
    public ResponseEntity<LessonResponse> create(@Valid @RequestBody LessonRequest request,
                                                 @AuthenticationPrincipal AuthUser current) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.create(request, current));
    }

    @Operation(summary = "List lessons of a module")
    @GetMapping("/module/{moduleId}")
    public List<LessonResponse> byModule(@PathVariable Long moduleId) {
        return lessonService.getByModule(moduleId);
    }

    @Operation(summary = "Get a lesson by id")
    @GetMapping("/{lessonId}")
    public LessonResponse getById(@PathVariable Long lessonId) {
        return lessonService.getById(lessonId);
    }

    @Operation(summary = "Update a lesson (owner INSTRUCTOR or ADMIN)")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PutMapping("/{id}")
    public LessonResponse update(@PathVariable Long id, @Valid @RequestBody LessonRequest request,
                                 @AuthenticationPrincipal AuthUser current) {
        return lessonService.update(id, request, current);
    }

    @Operation(summary = "Delete a lesson (owner INSTRUCTOR or ADMIN)")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        lessonService.delete(id, current);
        return ResponseEntity.noContent().build();
    }
}
