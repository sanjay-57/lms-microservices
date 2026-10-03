package com.lms.certificate.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CertificateNumbersTest {

    @Test
    void format_zeroPadsToSixDigits() {
        assertThat(CertificateNumbers.format(2026, 7)).isEqualTo("LMS-2026-000007");
        assertThat(CertificateNumbers.format(2026, 123456)).isEqualTo("LMS-2026-123456");
        assertThat(CertificateNumbers.format(2026, 42)).matches("LMS-\\d{4}-\\d{6}");
    }

    @Test
    void format_rejectsNonPositiveSequence() {
        assertThrows(IllegalArgumentException.class, () -> CertificateNumbers.format(2026, 0));
    }
}
