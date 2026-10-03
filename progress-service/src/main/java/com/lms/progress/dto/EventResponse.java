package com.lms.progress.dto;

import com.lms.progress.aggregate.EventData;
import com.lms.progress.entity.StoredEvent;

import java.time.LocalDateTime;
import java.util.Map;

/** One entry of an aggregate's event history, as stored in the event store. */
public record EventResponse(Integer version, String eventType, Map<String, Object> data, LocalDateTime createdAt) {

    public static EventResponse from(StoredEvent e) {
        return new EventResponse(e.getEventVersion(), e.getEventType(), EventData.fromJson(e.getEventData()), e.getCreatedAt());
    }
}
