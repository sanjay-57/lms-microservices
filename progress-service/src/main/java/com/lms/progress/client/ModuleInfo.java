package com.lms.progress.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModuleInfo(Long id, Long courseId, String title) { }
