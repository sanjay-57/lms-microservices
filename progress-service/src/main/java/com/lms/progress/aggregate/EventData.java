package com.lms.progress.aggregate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * (De)serialises the data map of a stored event to/from the JSON in event_store.event_data and offers
 * type-tolerant accessors (JSON numbers come back as Integer/Long/Double). Plain Java, no Spring.
 */
public final class EventData {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private EventData() { }

    public static String toJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise event data", ex);
        }
    }

    public static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Corrupt event data: " + json, ex);
        }
    }

    public static Long longValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof Number n ? n.longValue() : Long.valueOf(value.toString());
    }

    public static Integer intValue(Map<String, Object> data, String key) {
        Long value = longValue(data, key);
        return value == null ? null : value.intValue();
    }

    public static String stringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    public static BigDecimal decimalValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    public static boolean booleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Boolean b ? b : value != null && Boolean.parseBoolean(value.toString());
    }
}
