package com.lms.notification.messaging;

import com.lms.notification.event.DomainEvent;
import com.lms.notification.event.RoutingKeys;
import com.lms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consumes StudentEnrolled, CourseCompleted and CertificateGenerated from notification.queue. */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;

    @RabbitListener(queues = RoutingKeys.NOTIFICATION_QUEUE)
    public void onEvent(DomainEvent event) {
        log.info("Consumed event type={} eventId={} aggregateId={}",
                event.eventType(), event.eventId(), event.aggregateId());
        notificationService.handleEvent(event);
    }
}
