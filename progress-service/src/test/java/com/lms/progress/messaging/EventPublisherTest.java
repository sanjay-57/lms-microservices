package com.lms.progress.messaging;

import com.lms.progress.event.DomainEvent;
import com.lms.progress.event.EventTypes;
import com.lms.progress.event.RoutingKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @Test
    void publish_outsideTransaction_sendsToLmsExchangeWithRoutingKey() {
        EventPublisher publisher = new EventPublisher(rabbitTemplate);
        DomainEvent event = DomainEvent.of(EventTypes.STUDENT_ENROLLED, "4-1", Map.of("studentId", 4, "courseId", 1));

        publisher.publish(RoutingKeys.STUDENT_ENROLLED, event);

        verify(rabbitTemplate).convertAndSend("lms.events", "student.enrolled", event);
    }

    @Test
    void publish_brokerDown_doesNotThrowToCaller() {
        EventPublisher publisher = new EventPublisher(rabbitTemplate);
        DomainEvent event = DomainEvent.of(EventTypes.STUDENT_ENROLLED, "4-1", Map.of("studentId", 4));
        doThrow(new AmqpConnectException(new IOException("down")))
                .when(rabbitTemplate).convertAndSend("lms.events", "student.enrolled", event);

        assertDoesNotThrow(() -> publisher.publish(RoutingKeys.STUDENT_ENROLLED, event));
    }
}
