package com.lms.progress.messaging;

import com.lms.progress.event.DomainEvent;
import com.lms.progress.event.EventTypes;
import com.lms.progress.event.RoutingKeys;
import com.lms.progress.service.ProgressCommandService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Entry point of the command side: integration events from progress.queue become aggregate commands. */
@Component
@RequiredArgsConstructor
public class ProgressEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProgressEventListener.class);

    private final ProgressCommandService commandService;

    @RabbitListener(queues = RoutingKeys.PROGRESS_QUEUE)
    public void onEvent(DomainEvent event) {
        log.info("Consumed event type={} eventId={} aggregateId={}",
                event.eventType(), event.eventId(), event.aggregateId());
        switch (event.eventType()) {
            case EventTypes.STUDENT_ENROLLED -> commandService.handleStudentEnrolled(event);
            case EventTypes.MODULE_COMPLETED -> commandService.handleModuleCompleted(event);
            case EventTypes.ASSESSMENT_COMPLETED -> commandService.handleAssessmentCompleted(event);
            default -> log.warn("Ignoring unexpected event type {}", event.eventType());
        }
    }
}
