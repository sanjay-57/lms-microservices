package com.lms.notification.mail;

import com.lms.notification.entity.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * E-mail channel. With {@code notification.mail.enabled=false} (the default) e-mails are only logged, so the
 * service runs without any SMTP server. A delivery failure never propagates: it is reported as FAILED and the
 * in-app notification is still stored.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${notification.mail.enabled:false}") boolean enabled,
                       @Value("${notification.mail.from:no-reply@lms.local}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.from = from;
    }

    public NotificationStatus send(String to, String subject, String body) {
        if (!enabled) {
            log.info("[EMAIL] to={} subject={}\n{}", to, subject, body);
            return NotificationStatus.LOGGED;
        }
        if (to == null || to.isBlank()) {
            log.warn("[EMAIL] no recipient address, e-mail not sent (subject={})", subject);
            return NotificationStatus.FAILED;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.error("[EMAIL] mail enabled but no JavaMailSender configured (spring.mail.host); to={}", to);
            return NotificationStatus.FAILED;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("[EMAIL] sent to={} subject={}", to, subject);
            return NotificationStatus.SENT;
        } catch (MailException ex) {
            log.error("[EMAIL] delivery FAILED to={} subject={}: {}", to, subject, ex.getMessage());
            return NotificationStatus.FAILED;
        }
    }
}
