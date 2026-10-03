package com.lms.progress.service;

import com.lms.progress.dto.EventResponse;
import com.lms.progress.dto.RebuildResponse;
import com.lms.progress.entity.Progress;
import com.lms.progress.entity.StoredEvent;
import com.lms.progress.exception.ResourceNotFoundException;
import com.lms.progress.projection.ProgressProjector;
import com.lms.progress.repository.EventStoreRepository;
import com.lms.progress.repository.ProgressRepository;
import com.lms.progress.security.AuthUser;
import com.lms.progress.security.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event-sourcing operations that work directly on the event store: browsing an aggregate's history and
 * rebuilding the whole read model by replaying every stored event through {@link ProgressProjector}.
 */
@Service
@RequiredArgsConstructor
public class EventStoreService {

    private static final Logger log = LoggerFactory.getLogger(EventStoreService.class);

    private final EventStoreRepository eventStoreRepository;
    private final ProgressRepository progressRepository;
    private final ProgressProjector projector;

    @Transactional(readOnly = true)
    public List<EventResponse> getHistory(String aggregateId, AuthUser current) {
        if (current.getRole() == Role.STUDENT && !aggregateId.startsWith(current.getId() + "-")) {
            throw new AccessDeniedException("You can only view the history of your own progress");
        }
        List<StoredEvent> events = eventStoreRepository.findByAggregateIdOrderByEventVersionAsc(aggregateId);
        if (events.isEmpty()) {
            throw new ResourceNotFoundException("No events found for aggregate " + aggregateId);
        }
        return events.stream().map(EventResponse::from).toList();
    }

    /**
     * Drops the read model and re-creates it from the event store. Because the projection is a pure
     * function of the event stream, the result equals the live read model.
     */
    @Transactional
    public RebuildResponse rebuildReadModel() {
        long start = System.currentTimeMillis();
        progressRepository.deleteAllInBatch();

        List<StoredEvent> events = eventStoreRepository.findAllByOrderByAggregateIdAscEventVersionAsc();
        Map<String, Progress> rows = new LinkedHashMap<>();
        for (StoredEvent event : events) {
            projector.applyTo(rows.computeIfAbsent(event.getAggregateId(), id -> new Progress()), event);
        }
        progressRepository.saveAll(rows.values());

        log.info("READ MODEL REBUILT: {} aggregate(s) from {} event(s) in {} ms",
                rows.size(), events.size(), System.currentTimeMillis() - start);
        return new RebuildResponse(rows.size(), events.size());
    }
}
