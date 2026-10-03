package com.lms.certificate.controller;

import com.lms.certificate.dto.CertificatePdf;
import com.lms.certificate.dto.CertificateResponse;
import com.lms.certificate.dto.CertificateVerificationResponse;
import com.lms.certificate.security.AuthUser;
import com.lms.certificate.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@Tag(name = "Certificates", description = "Certificates issued on CourseCompleted; PDF download and public verification")
public class CertificateController {

    private final CertificateService certificateService;

    @Operation(summary = "List a student's certificates (self, INSTRUCTOR or ADMIN)")
    @GetMapping("/student/{studentId}")
    public List<CertificateResponse> byStudent(@PathVariable Long studentId, @AuthenticationPrincipal AuthUser current) {
        return certificateService.getByStudent(studentId, current);
    }

    @Operation(summary = "Get a certificate by id (owner, INSTRUCTOR or ADMIN)")
    @GetMapping("/{id}")
    public CertificateResponse getById(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        return certificateService.getById(id, current);
    }

    @Operation(summary = "Download the certificate as PDF (owner, INSTRUCTOR or ADMIN)")
    @ApiResponse(responseCode = "200", description = "application/pdf attachment")
    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        CertificatePdf pdf = certificateService.download(id, current);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .contentLength(pdf.content().length)
                .body(pdf.content());
    }

    @Operation(summary = "Verify a certificate by its verification code (PUBLIC, no token)")
    @ApiResponse(responseCode = "404", description = "Unknown code ({valid:false, ...})")
    @SecurityRequirements
    @GetMapping("/verify/{code}")
    public CertificateVerificationResponse verify(@PathVariable String code) {
        return certificateService.verify(code);
    }
}
