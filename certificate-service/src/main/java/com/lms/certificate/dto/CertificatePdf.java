package com.lms.certificate.dto;

/** A rendered certificate ready to be streamed to the client. */
public record CertificatePdf(String filename, byte[] content) { }
