package com.lms.enrollment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lms.enrollment.event.DomainEvent;
import com.lms.enrollment.event.EventTypes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the JSON schema shared by all services: { eventId, eventType, timestamp, aggregateId, payload }. */
class DomainEventJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void event_roundTripsThroughJson_andHasEnvelopeFields() throws Exception {
        DomainEvent event = DomainEvent.of(EventTypes.STUDENT_ENROLLED, "4-1", Map.of("studentId", 4, "courseId", 1));

        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"eventId\"", "\"eventType\":\"StudentEnrolled\"",
                "\"timestamp\"", "\"aggregateId\":\"4-1\"", "\"payload\"");

        DomainEvent back = mapper.readValue(json, DomainEvent.class);
        assertThat(back.eventId()).isEqualTo(event.eventId());
        assertThat(back.longValue("studentId")).isEqualTo(4L);
        assertThat(back.timestamp()).isEqualTo(event.timestamp());
    }
}
