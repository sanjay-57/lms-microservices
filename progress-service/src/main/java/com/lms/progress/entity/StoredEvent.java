package com.lms.progress.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One immutable entry of the append-only event store (write side of CQRS). The current state of a
 * CourseProgress aggregate is never stored directly; it is rebuilt by replaying these rows in version order.
 * UNIQUE(aggregate_id, event_version) gives optimistic concurrency: two writers that loaded the same
 * version cannot both append the next one - the loser gets a constraint violation and the message is retried.
 */
@Entity
@Table(name = "event_store",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_store_aggregate_version",
                columnNames = {"aggregate_id", "event_version"}),
        indexes = @Index(name = "idx_event_store_aggregate", columnList = "aggregate_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredEvent {

    public static final String AGGREGATE_TYPE = "CourseProgress";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "&lt;studentId&gt;-&lt;courseId&gt;", same convention as the integration event envelope. */
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** JSON object with the event's data map. */
    @Column(name = "event_data", nullable = false, columnDefinition = "TEXT")
    private String eventData;

    /** 1-based, gap-free sequence number within the aggregate. */
    @Column(name = "event_version", nullable = false)
    private Integer eventVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (aggregateType == null) {
            aggregateType = AGGREGATE_TYPE;
        }
    }
}
