package com.lms.certificate.messaging;

import com.lms.certificate.event.DomainEvent;
import com.lms.certificate.event.EventTypes;
import com.lms.certificate.event.RoutingKeys;
import com.lms.certificate.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CertificateEventListener {

    private static final Logger log = LoggerFactory.getLogger(CertificateEventListener.class);

    private final CertificateService certificateService;

    @RabbitListener(queues = RoutingKeys.CERTIFICATE_QUEUE)
    public void onEvent(DomainEvent event) {
        log.info("Consumed event type={} eventId={} aggregateId={}",
                event.eventType(), event.eventId(), event.aggregateId());
        if (EventTypes.COURSE_COMPLETED.equals(event.eventType())) {
            certificateService.handleCourseCompleted(event);
        } else {
            log.warn("Ignoring unexpected event type {}", event.eventType());
        }
    }
}
