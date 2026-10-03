package com.lms.notification.entity;

/** Outcome of the e-mail channel; the in-app notification is stored in every case. */
public enum NotificationStatus {
    /** E-mail handed to the SMTP server. */
    SENT,
    /** Mail disabled (default for local runs): the e-mail was only written to the log. */
    LOGGED,
    /** SMTP delivery failed; the notification is still visible in-app. */
    FAILED
}
