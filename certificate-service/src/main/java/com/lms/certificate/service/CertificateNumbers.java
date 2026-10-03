package com.lms.certificate.service;

/** Certificate number format: {@code LMS-<year>-<6-digit zero-padded sequence>}, e.g. LMS-2026-000042. */
public final class CertificateNumbers {

    private CertificateNumbers() { }

    public static String format(int year, long sequence) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("Certificate sequence must be positive");
        }
        return String.format("LMS-%d-%06d", year, sequence);
    }
}
