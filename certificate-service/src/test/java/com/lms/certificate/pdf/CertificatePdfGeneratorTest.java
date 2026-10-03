package com.lms.certificate.pdf;

import com.lms.certificate.entity.Certificate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfGeneratorTest {

    private final CertificatePdfGenerator generator =
            new CertificatePdfGenerator(new QrCodeGenerator(), "http://localhost:5173/verify/");

    private Certificate certificate() {
        return Certificate.builder()
                .id(42L).certificateNumber("LMS-2026-000042").studentId(4L).courseId(1L)
                .studentName("Alice Student").courseName("Spring Boot Microservices")
                .issueDate(LocalDateTime.of(2026, 10, 3, 10, 0))
                .completionDate(LocalDateTime.of(2026, 10, 2, 18, 30))
                .finalScore(new BigDecimal("87.50"))
                .verificationCode("3f1c2d9e-0000-4000-8000-123456789abc")
                .certificateUrl("/api/certificates/42/download")
                .build();
    }

    @Test
    void generate_producesPdfBytes() {
        byte[] pdf = generator.generate(certificate());

        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void generate_toleratesMissingOptionalData() {
        Certificate c = Certificate.builder().id(1L).studentId(4L).courseId(1L).build();

        byte[] pdf = generator.generate(c);

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void verificationUrl_usesConfiguredBaseWithoutDoubleSlash() {
        assertThat(generator.verificationUrl(certificate()))
                .isEqualTo("http://localhost:5173/verify/3f1c2d9e-0000-4000-8000-123456789abc");
    }

    @Test
    void qrCode_isPng() {
        byte[] png = new QrCodeGenerator().generatePng("http://localhost:5173/verify/abc", 200);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');
    }
}
