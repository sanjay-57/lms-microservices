package com.lms.content.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Common event envelope shared (as an identical JSON schema) by every LMS service:
 * { eventId, eventType, timestamp, aggregateId, payload }.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DomainEvent(String eventId,
                          String eventType,
                          Instant timestamp,
                          String aggregateId,
                          Map<String, Object> payload) {

    public static DomainEvent of(String eventType, String aggregateId, Map<String, Object> payload) {
        return new DomainEvent(UUID.randomUUID().toString(), eventType, Instant.now(), aggregateId, payload);
    }

    /** Aggregate id convention used by all events of one student's course journey. */
    public static String aggregateId(Long studentId, Long courseId) {
        return studentId + "-" + courseId;
    }

    public Long longValue(String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    public String stringValue(String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : value.toString();
    }
}
