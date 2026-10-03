package com.lms.certificate.exception;

/** Thrown by the public verification endpoint when no certificate matches the verification code. */
public class InvalidCertificateException extends RuntimeException {
    public InvalidCertificateException(String message) {
        super(message);
    }
}
