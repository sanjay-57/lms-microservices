package com.lms.progress.aggregate;

import java.util.Map;

/**
 * An event produced by a command method of {@link CourseProgressAggregate} that has already been applied
 * to the in-memory aggregate but not yet appended to the event store.
 *
 * @param version the version the event must be stored with (previous version + 1)
 */
public record NewEvent(String eventType, Map<String, Object> data, int version) { }
