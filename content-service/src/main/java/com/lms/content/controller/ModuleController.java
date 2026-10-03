package com.lms.content.controller;

import com.lms.content.dto.ModuleCompletionResponse;
import com.lms.content.dto.ModuleRequest;
import com.lms.content.dto.ModuleResponse;
import com.lms.content.security.AuthUser;
import com.lms.content.service.ModuleCompletionService;
import com.lms.content.service.ModuleService;
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
@RequestMapping("/api/modules")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Modules", description = "Course modules")
public class ModuleController {

    private final ModuleService moduleService;
    private final ModuleCompletionService moduleCompletionService;

    @Operation(summary = "Create a module (owner INSTRUCTOR or ADMIN)")
    @ApiResponse(responseCode = "201", description = "Module created")
    @ApiResponse(responseCode = "409", description = "Sequence number already used in this course")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PostMapping
    public ResponseEntity<ModuleResponse> create(@Valid @RequestBody ModuleRequest request,
                                                 @AuthenticationPrincipal AuthUser current) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moduleService.create(request, current));
    }

    @Operation(summary = "List modules of a course ordered by sequence number")
    @GetMapping("/course/{courseId}")
    public List<ModuleResponse> byCourse(@PathVariable Long courseId) {
        return moduleService.getByCourse(courseId);
    }

    @Operation(summary = "Get a module by id")
    @GetMapping("/{moduleId}")
    public ModuleResponse getById(@PathVariable Long moduleId) {
        return moduleService.getById(moduleId);
    }

    @Operation(summary = "Update a module (owner INSTRUCTOR or ADMIN)")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PutMapping("/{id}")
    public ModuleResponse update(@PathVariable Long id, @Valid @RequestBody ModuleRequest request,
                                 @AuthenticationPrincipal AuthUser current) {
        return moduleService.update(id, request, current);
    }

    @Operation(summary = "Delete a module and its lessons (owner INSTRUCTOR or ADMIN)")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        moduleService.delete(id, current);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark a module as completed (enrolled STUDENT); publishes the ModuleCompleted event")
    @ApiResponse(responseCode = "200", description = "Module completed")
    @ApiResponse(responseCode = "403", description = "Not actively enrolled in the course")
    @ApiResponse(responseCode = "409", description = "Module already completed")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{moduleId}/complete")
    public ModuleCompletionResponse complete(@PathVariable Long moduleId, @AuthenticationPrincipal AuthUser current) {
        return moduleCompletionService.complete(moduleId, current);
    }

    @Operation(summary = "Ids of the modules of a course the current student has completed")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/course/{courseId}/completed")
    public List<Long> completed(@PathVariable Long courseId, @AuthenticationPrincipal AuthUser current) {
        return moduleCompletionService.completedModuleIds(courseId, current);
    }
}
