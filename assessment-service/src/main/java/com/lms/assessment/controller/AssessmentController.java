package com.lms.assessment.controller;

import com.lms.assessment.dto.*;
import com.lms.assessment.security.AuthUser;
import com.lms.assessment.service.AssessmentService;
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
@RequestMapping("/api/assessments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Assessments", description = "Assessments, questions and automatic scoring")
public class AssessmentController {

    private final AssessmentService assessmentService;

    @Operation(summary = "Create an assessment for a course (owner INSTRUCTOR or ADMIN)")
    @ApiResponse(responseCode = "201", description = "Assessment created")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PostMapping
    public ResponseEntity<AssessmentResponse> create(@Valid @RequestBody AssessmentRequest request,
                                                     @AuthenticationPrincipal AuthUser current) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assessmentService.create(request, current));
    }

    @Operation(summary = "List assessments of a course")
    @GetMapping("/course/{courseId}")
    public List<AssessmentResponse> byCourse(@PathVariable Long courseId) {
        return assessmentService.getByCourse(courseId);
    }

    @Operation(summary = "Get an assessment by id")
    @GetMapping("/{id}")
    public AssessmentResponse getById(@PathVariable Long id) {
        return assessmentService.getById(id);
    }

    @Operation(summary = "Add a question (owner INSTRUCTOR or ADMIN); increases the assessment's totalMarks")
    @ApiResponse(responseCode = "201", description = "Question added")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PostMapping("/{id}/questions")
    public ResponseEntity<QuestionResponse> addQuestion(@PathVariable Long id,
                                                        @Valid @RequestBody QuestionRequest request,
                                                        @AuthenticationPrincipal AuthUser current) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assessmentService.addQuestion(id, request, current));
    }

    @Operation(summary = "List questions (correct answers are hidden from students)")
    @GetMapping("/{id}/questions")
    public List<QuestionResponse> questions(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        return assessmentService.getQuestions(id, current);
    }

    @Operation(summary = "Submit answers (STUDENT). Scores automatically and publishes AssessmentCompleted")
    @ApiResponse(responseCode = "200", description = "Scored submission")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/submit")
    public SubmissionResponse submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest request,
                                     @AuthenticationPrincipal AuthUser current) {
        return assessmentService.submit(id, request, current);
    }
}
