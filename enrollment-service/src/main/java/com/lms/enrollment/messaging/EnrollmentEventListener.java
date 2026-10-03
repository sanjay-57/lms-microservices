package com.lms.enrollment.messaging;

import com.lms.enrollment.event.DomainEvent;
import com.lms.enrollment.event.EventTypes;
import com.lms.enrollment.event.RoutingKeys;
import com.lms.enrollment.service.EnrollmentSagaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentEventListener.class);

    private final EnrollmentSagaService sagaService;

    @RabbitListener(queues = RoutingKeys.ENROLLMENT_QUEUE)
    public void onEvent(DomainEvent event) {
        log.info("Consumed event type={} eventId={} aggregateId={}",
                event.eventType(), event.eventId(), event.aggregateId());
        switch (event.eventType()) {
            case EventTypes.ENROLLMENT_FAILED -> sagaService.handleEnrollmentFailed(event);
            case EventTypes.COURSE_COMPLETED -> sagaService.handleCourseCompleted(event);
            default -> log.warn("Ignoring unexpected event type {}", event.eventType());
        }
    }
}
