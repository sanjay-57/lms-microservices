package com.lms.notification.mail;

import com.lms.notification.entity.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    @Mock private ObjectProvider<JavaMailSender> provider;
    @Mock private JavaMailSender mailSender;

    @Test
    void mailDisabled_onlyLogs() {
        EmailSender sender = new EmailSender(provider, false, "no-reply@lms.local");

        assertThat(sender.send("alice@lms.local", "Hi", "Body")).isEqualTo(NotificationStatus.LOGGED);
        verifyNoInteractions(provider, mailSender);
    }

    @Test
    void mailEnabled_sendsWithConfiguredFrom() {
        when(provider.getIfAvailable()).thenReturn(mailSender);
        EmailSender sender = new EmailSender(provider, true, "no-reply@lms.local");

        assertThat(sender.send("alice@lms.local", "Hi", "Body")).isEqualTo(NotificationStatus.SENT);

        ArgumentCaptor<SimpleMailMessage> msg = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(msg.capture());
        assertThat(msg.getValue().getFrom()).isEqualTo("no-reply@lms.local");
        assertThat(msg.getValue().getTo()).containsExactly("alice@lms.local");
        assertThat(msg.getValue().getSubject()).isEqualTo("Hi");
    }

    @Test
    void mailFailure_returnsFailedWithoutThrowing() {
        when(provider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        EmailSender sender = new EmailSender(provider, true, "no-reply@lms.local");

        NotificationStatus[] status = new NotificationStatus[1];
        assertDoesNotThrow(() -> status[0] = sender.send("alice@lms.local", "Hi", "Body"));
        assertThat(status[0]).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void mailEnabled_withoutRecipient_isFailed() {
        EmailSender sender = new EmailSender(provider, true, "no-reply@lms.local");
        assertThat(sender.send(null, "Hi", "Body")).isEqualTo(NotificationStatus.FAILED);
        verifyNoInteractions(mailSender);
    }
}
