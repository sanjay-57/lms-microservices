package com.lms.progress.messaging;

import com.lms.progress.event.DomainEvent;
import com.lms.progress.event.RoutingKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes domain events to the lms.events exchange. When called inside a database transaction the
 * message is sent only AFTER the transaction commits, so consumers never see events for rolled-back data.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String routingKey, DomainEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(routingKey, event);
                }
            });
        } else {
            send(routingKey, event);
        }
    }

    private void send(String routingKey, DomainEvent event) {
        try {
            rabbitTemplate.convertAndSend(RoutingKeys.EXCHANGE, routingKey, event);
            log.info("Published event type={} eventId={} routingKey={} aggregateId={}",
                    event.eventType(), event.eventId(), routingKey, event.aggregateId());
        } catch (AmqpException ex) {
            log.error("FAILED to publish event type={} eventId={} routingKey={}: {}",
                    event.eventType(), event.eventId(), routingKey, ex.getMessage());
        }
    }
}
