package com.lms.progress.dto;

/** Result of a read-model rebuild: number of aggregates re-projected and events replayed. */
public record RebuildResponse(long aggregates, long events) { }
