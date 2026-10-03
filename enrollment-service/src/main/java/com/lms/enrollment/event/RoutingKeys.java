package com.lms.enrollment.event;

/** Exchange, queue and routing-key names of the LMS RabbitMQ topology. */
public final class RoutingKeys {

    public static final String EXCHANGE = "lms.events";
    public static final String DLX = "lms.events.dlx";

    public static final String PROGRESS_QUEUE = "progress.queue";
    public static final String CERTIFICATE_QUEUE = "certificate.queue";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String ENROLLMENT_QUEUE = "enrollment.queue";

    public static final String STUDENT_ENROLLED = "student.enrolled";
    public static final String MODULE_COMPLETED = "module.completed";
    public static final String ASSESSMENT_COMPLETED = "assessment.completed";
    public static final String COURSE_COMPLETED = "course.completed";
    public static final String CERTIFICATE_GENERATED = "certificate.generated";
    public static final String ENROLLMENT_FAILED = "enrollment.failed";

    private RoutingKeys() { }
}
