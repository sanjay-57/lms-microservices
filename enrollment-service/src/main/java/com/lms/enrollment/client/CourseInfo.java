package com.lms.enrollment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseInfo(Long id, String title, Long instructorId, String status) { }
