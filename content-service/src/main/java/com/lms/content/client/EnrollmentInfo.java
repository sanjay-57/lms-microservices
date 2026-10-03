package com.lms.content.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EnrollmentInfo(Long id, Long studentId, Long courseId, String status) { }
