package com.lms.progress.repository;

import com.lms.progress.entity.StoredEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Append-only access to the event store: events are inserted and read, never updated or deleted. */
public interface EventStoreRepository extends JpaRepository<StoredEvent, Long> {

    List<StoredEvent> findByAggregateIdOrderByEventVersionAsc(String aggregateId);

    /** Whole store in replay order (per aggregate, by version) - used to rebuild the read model. */
    List<StoredEvent> findAllByOrderByAggregateIdAscEventVersionAsc();
}
