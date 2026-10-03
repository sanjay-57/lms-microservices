package com.lms.assessment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EnrollmentInfo(Long id, Long studentId, Long courseId, String status) { }
