package com.lms.certificate.exception;

import java.time.LocalDateTime;

/** Standard error body plus {@code valid=false}, so the verification page can rely on a single flag. */
public record VerificationErrorResponse(boolean valid, LocalDateTime timestamp, int status, String message,
                                        String path) { }
