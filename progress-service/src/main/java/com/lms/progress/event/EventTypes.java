package com.lms.progress.event;

public final class EventTypes {

    public static final String STUDENT_ENROLLED = "StudentEnrolled";
    public static final String MODULE_COMPLETED = "ModuleCompleted";
    public static final String ASSESSMENT_COMPLETED = "AssessmentCompleted";
    public static final String COURSE_COMPLETED = "CourseCompleted";
    public static final String CERTIFICATE_GENERATED = "CertificateGenerated";
    /** Saga compensation event published when progress initialisation fails. */
    public static final String ENROLLMENT_FAILED = "EnrollmentFailed";

    private EventTypes() { }
}
