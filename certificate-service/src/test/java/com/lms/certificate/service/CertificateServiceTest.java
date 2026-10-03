package com.lms.certificate.service;

import com.lms.certificate.dto.CertificatePdf;
import com.lms.certificate.entity.Certificate;
import com.lms.certificate.entity.ProcessedEvent;
import com.lms.certificate.event.DomainEvent;
import com.lms.certificate.event.EventTypes;
import com.lms.certificate.event.RoutingKeys;
import com.lms.certificate.exception.InvalidCertificateException;
import com.lms.certificate.messaging.EventPublisher;
import com.lms.certificate.pdf.CertificatePdfGenerator;
import com.lms.certificate.repository.CertificateRepository;
import com.lms.certificate.repository.ProcessedEventRepository;
import com.lms.certificate.security.AuthUser;
import com.lms.certificate.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock private CertificateRepository certificateRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private CertificatePdfGenerator pdfGenerator;
    @InjectMocks private CertificateService service;

    private DomainEvent courseCompleted() {
        return DomainEvent.of(EventTypes.COURSE_COMPLETED, "4-1", Map.of(
                "enrollmentId", 9, "studentId", 4, "studentName", "Alice", "studentEmail", "alice@lms.local",
                "courseId", 1, "courseTitle", "Microservices", "finalScore", 87.5,
                "completionDate", "2026-10-02T18:30:00"));
    }

    @Test
    void courseCompleted_issuesCertificateAndPublishesCertificateGenerated() {
        DomainEvent event = courseCompleted();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(certificateRepository.existsByStudentIdAndCourseId(4L, 1L)).thenReturn(false);
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> {
            Certificate c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(42L);
            }
            return c;
        });

        service.handleCourseCompleted(event);

        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eq(RoutingKeys.CERTIFICATE_GENERATED), published.capture());
        DomainEvent out = published.getValue();
        int year = LocalDateTime.now().getYear();
        assertThat(out.eventType()).isEqualTo(EventTypes.CERTIFICATE_GENERATED);
        assertThat(out.aggregateId()).isEqualTo("4-1");
        assertThat(out.payload())
                .containsEntry("certificateId", 42L)
                .containsEntry("certificateNumber", "LMS-" + year + "-000042")
                .containsEntry("studentId", 4L)
                .containsEntry("studentName", "Alice")
                .containsEntry("studentEmail", "alice@lms.local")
                .containsEntry("courseId", 1L)
                .containsEntry("courseTitle", "Microservices")
                .containsEntry("certificateUrl", "/api/certificates/42/download")
                .containsKey("verificationCode");

        ArgumentCaptor<Certificate> saved = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository, atLeastOnce()).save(saved.capture());
        Certificate c = saved.getValue();
        assertThat(c.getFinalScore()).isEqualByComparingTo(new BigDecimal("87.50"));
        assertThat(c.getCompletionDate()).isEqualTo(LocalDateTime.of(2026, 10, 2, 18, 30));
        assertThat(c.getVerificationCode()).hasSize(36);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void duplicateEventId_isIgnored() {
        DomainEvent event = courseCompleted();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        service.handleCourseCompleted(event);

        verifyNoInteractions(certificateRepository, eventPublisher);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void secondCourseCompletedForSameStudentAndCourse_doesNotCreateOrPublishAgain() {
        DomainEvent event = courseCompleted(); // new eventId, same student/course
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(certificateRepository.existsByStudentIdAndCourseId(4L, 1L)).thenReturn(true);

        service.handleCourseCompleted(event);

        verify(certificateRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void courseCompleted_withoutIds_throwsSoMessageGoesToDlq() {
        DomainEvent event = DomainEvent.of(EventTypes.COURSE_COMPLETED, "x", Map.of("courseTitle", "X"));
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.handleCourseCompleted(event));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void download_ownerGetsPdfWithNumberedFilename() {
        Certificate c = Certificate.builder().id(42L).studentId(4L).courseId(1L).certificateNumber("LMS-2026-000042").build();
        when(certificateRepository.findById(42L)).thenReturn(Optional.of(c));
        when(pdfGenerator.generate(c)).thenReturn("%PDF-1.4".getBytes());

        CertificatePdf pdf = service.download(42L, new AuthUser(4L, "alice@lms.local", Role.STUDENT, "t"));

        assertThat(pdf.filename()).isEqualTo("certificate-LMS-2026-000042.pdf");
    }

    @Test
    void download_otherStudent_isDenied() {
        Certificate c = Certificate.builder().id(42L).studentId(4L).courseId(1L).build();
        when(certificateRepository.findById(42L)).thenReturn(Optional.of(c));

        assertThrows(AccessDeniedException.class,
                () -> service.download(42L, new AuthUser(5L, "bob@lms.local", Role.STUDENT, "t")));
        verifyNoInteractions(pdfGenerator);
    }

    @Test
    void getByStudent_instructorMayViewAnyStudent() {
        service.getByStudent(4L, new AuthUser(2L, "teacher@lms.local", Role.INSTRUCTOR, "t"));
        verify(certificateRepository).findByStudentIdOrderByIssueDateDesc(4L);
    }

    @Test
    void verify_unknownCode_throwsInvalidCertificate() {
        when(certificateRepository.findByVerificationCode("nope")).thenReturn(Optional.empty());
        assertThrows(InvalidCertificateException.class, () -> service.verify("nope"));
    }

    @Test
    void parseDate_acceptsZonedIsoStrings() {
        assertThat(CertificateService.parseDate("2026-10-02T18:30:00Z")).isEqualTo(LocalDateTime.of(2026, 10, 2, 18, 30));
        assertThat(CertificateService.parseDate("2026-10-02")).isEqualTo(LocalDateTime.of(2026, 10, 2, 0, 0));
    }
}
