package com.lms.notification.messaging;

import com.lms.notification.event.DomainEvent;
import com.lms.notification.event.EventTypes;
import com.lms.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationService notificationService;
    @InjectMocks private NotificationEventListener listener;

    @Test
    void onEvent_delegatesToService() {
        DomainEvent event = DomainEvent.of(EventTypes.STUDENT_ENROLLED, "4-1", Map.of("studentId", 4));
        listener.onEvent(event);
        verify(notificationService).handleEvent(event);
    }
}
