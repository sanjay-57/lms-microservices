package com.lms.certificate.service;

import com.lms.certificate.dto.CertificatePdf;
import com.lms.certificate.dto.CertificateResponse;
import com.lms.certificate.dto.CertificateVerificationResponse;
import com.lms.certificate.entity.Certificate;
import com.lms.certificate.entity.ProcessedEvent;
import com.lms.certificate.event.DomainEvent;
import com.lms.certificate.event.EventTypes;
import com.lms.certificate.event.RoutingKeys;
import com.lms.certificate.exception.InvalidCertificateException;
import com.lms.certificate.exception.ResourceNotFoundException;
import com.lms.certificate.messaging.EventPublisher;
import com.lms.certificate.pdf.CertificatePdfGenerator;
import com.lms.certificate.repository.CertificateRepository;
import com.lms.certificate.repository.ProcessedEventRepository;
import com.lms.certificate.security.AuthUser;
import com.lms.certificate.security.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepository certificateRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EventPublisher eventPublisher;
    private final CertificatePdfGenerator pdfGenerator;

    /**
     * CourseCompleted -> issue a certificate and publish CertificateGenerated (after commit).
     * Idempotent twice over: the event id is recorded in processed_events, and a student can hold at most one
     * certificate per course (UNIQUE(student_id, course_id)), so even a re-published completion is a no-op.
     */
    @Transactional
    public void handleCourseCompleted(DomainEvent event) {
        if (alreadyProcessed(event)) {
            return;
        }
        Long studentId = event.longValue("studentId");
        Long courseId = event.longValue("courseId");
        if (studentId == null || courseId == null) {
            throw new IllegalArgumentException("CourseCompleted without studentId/courseId, eventId=" + event.eventId());
        }
        if (certificateRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            log.info("Certificate already exists for studentId={} courseId={}, nothing to issue", studentId, courseId);
            markProcessed(event);
            return;
        }

        Certificate certificate = certificateRepository.save(Certificate.builder()
                .studentId(studentId)
                .courseId(courseId)
                .studentName(event.stringValue("studentName"))
                .courseName(event.stringValue("courseTitle"))
                .completionDate(parseDate(event.stringValue("completionDate")))
                .finalScore(parseScore(event.payload() == null ? null : event.payload().get("finalScore")))
                .issueDate(LocalDateTime.now())
                .verificationCode(UUID.randomUUID().toString())
                .build());
        // the number and download URL are derived from the generated id (dirty-checked, flushed on commit)
        certificate.setCertificateNumber(CertificateNumbers.format(certificate.getIssueDate().getYear(), certificate.getId()));
        certificate.setCertificateUrl("/api/certificates/" + certificate.getId() + "/download");
        certificate = certificateRepository.save(certificate);
        log.info("Certificate {} issued: id={}, studentId={}, courseId={}",
                certificate.getCertificateNumber(), certificate.getId(), studentId, courseId);

        Map<String, Object> payload = new LinkedHashMap<>();   // HashMap: studentEmail may be null
        payload.put("certificateId", certificate.getId());
        payload.put("certificateNumber", certificate.getCertificateNumber());
        payload.put("verificationCode", certificate.getVerificationCode());
        payload.put("studentId", studentId);
        payload.put("studentName", certificate.getStudentName());
        payload.put("studentEmail", event.stringValue("studentEmail"));
        payload.put("courseId", courseId);
        payload.put("courseTitle", certificate.getCourseName());
        payload.put("certificateUrl", certificate.getCertificateUrl());
        eventPublisher.publish(RoutingKeys.CERTIFICATE_GENERATED,
                DomainEvent.of(EventTypes.CERTIFICATE_GENERATED, DomainEvent.aggregateId(studentId, courseId), payload));
        markProcessed(event);
    }

    @Transactional(readOnly = true)
    public List<CertificateResponse> getByStudent(Long studentId, AuthUser current) {
        if (current.getRole() == Role.STUDENT && !current.getId().equals(studentId)) {
            throw new AccessDeniedException("You can only view your own certificates");
        }
        return certificateRepository.findByStudentIdOrderByIssueDateDesc(studentId)
                .stream().map(CertificateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CertificateResponse getById(Long id, AuthUser current) {
        return CertificateResponse.from(findAccessible(id, current));
    }

    /** Renders the PDF on the fly (no files on disk). */
    @Transactional(readOnly = true)
    public CertificatePdf download(Long id, AuthUser current) {
        Certificate certificate = findAccessible(id, current);
        byte[] pdf = pdfGenerator.generate(certificate);
        log.info("Certificate {} downloaded by userId={}", certificate.getCertificateNumber(), current.getId());
        return new CertificatePdf("certificate-" + certificate.getCertificateNumber() + ".pdf", pdf);
    }

    /** Public verification (target of the QR code). */
    @Transactional(readOnly = true)
    public CertificateVerificationResponse verify(String verificationCode) {
        return certificateRepository.findByVerificationCode(verificationCode)
                .map(CertificateVerificationResponse::from)
                .orElseThrow(() -> new InvalidCertificateException("No certificate found for this verification code"));
    }

    private Certificate findAccessible(Long id, AuthUser current) {
        Certificate certificate = certificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found with id " + id));
        if (current.getRole() == Role.STUDENT && !certificate.getStudentId().equals(current.getId())) {
            throw new AccessDeniedException("You can only access your own certificates");
        }
        return certificate;
    }

    /** completionDate is an ISO-8601 string; accept both local and zoned/instant forms. */
    static LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // try the zoned forms below
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // try a plain date
        }
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException ex) {
            log.warn("Unparseable completionDate '{}', using now", value);
            return LocalDateTime.now();
        }
    }

    static BigDecimal parseScore(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            log.warn("Unparseable finalScore '{}'", value);
            return null;
        }
    }

    private boolean alreadyProcessed(DomainEvent event) {
        if (event.eventId() == null) {
            throw new IllegalArgumentException("Event without eventId");
        }
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Duplicate event ignored: type={} eventId={}", event.eventType(), event.eventId());
            return true;
        }
        return false;
    }

    private void markProcessed(DomainEvent event) {
        processedEventRepository.save(new ProcessedEvent(event.eventId(), event.eventType(), LocalDateTime.now()));
    }
}
