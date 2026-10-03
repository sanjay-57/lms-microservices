package com.lms.notification.config;

import com.lms.notification.event.RoutingKeys;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Declares the complete LMS messaging topology. The declaration is idempotent and identical in every
 * messaging service, so whichever service starts first creates everything (no lost events).
 *
 * lms.events (topic)
 *   student.enrolled       -> progress.queue, notification.queue
 *   module.completed       -> progress.queue
 *   assessment.completed   -> progress.queue
 *   course.completed       -> certificate.queue, notification.queue, enrollment.queue
 *   certificate.generated  -> notification.queue
 *   enrollment.failed      -> enrollment.queue   (saga compensation)
 * Every queue has a dead-letter queue "<name>.dlq" (via lms.events.dlx).
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public Declarables lmsTopology() {
        TopicExchange events = new TopicExchange(RoutingKeys.EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(RoutingKeys.DLX, true, false);
        List<Declarable> items = new ArrayList<>();
        items.add(events);
        items.add(dlx);

        declare(items, events, dlx, RoutingKeys.PROGRESS_QUEUE,
                RoutingKeys.STUDENT_ENROLLED, RoutingKeys.MODULE_COMPLETED, RoutingKeys.ASSESSMENT_COMPLETED);
        declare(items, events, dlx, RoutingKeys.CERTIFICATE_QUEUE,
                RoutingKeys.COURSE_COMPLETED);
        declare(items, events, dlx, RoutingKeys.NOTIFICATION_QUEUE,
                RoutingKeys.STUDENT_ENROLLED, RoutingKeys.COURSE_COMPLETED, RoutingKeys.CERTIFICATE_GENERATED);
        declare(items, events, dlx, RoutingKeys.ENROLLMENT_QUEUE,
                RoutingKeys.ENROLLMENT_FAILED, RoutingKeys.COURSE_COMPLETED);
        return new Declarables(items);
    }

    private void declare(List<Declarable> items, TopicExchange events, DirectExchange dlx,
                         String queueName, String... routingKeys) {
        Queue queue = QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", RoutingKeys.DLX)
                .withArgument("x-dead-letter-routing-key", queueName + ".dlq")
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(queueName + ".dlq").build();
        items.add(queue);
        items.add(deadLetterQueue);
        items.add(BindingBuilder.bind(deadLetterQueue).to(dlx).with(queueName + ".dlq"));
        for (String key : routingKeys) {
            items.add(BindingBuilder.bind(queue).to(events).with(key));
        }
    }
}
