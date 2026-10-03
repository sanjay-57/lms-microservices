package com.lms.progress.aggregate;

/**
 * Internal event-sourcing event types of the CourseProgress aggregate (stored in event_store).
 * Some share their name with an integration event, but they are a separate, private vocabulary:
 * integration events are inputs/outputs on RabbitMQ, these are the facts this service records.
 */
public final class ProgressEventTypes {

    public static final String PROGRESS_INITIALIZED = "ProgressInitialized";
    public static final String PROGRESS_INITIALIZATION_FAILED = "ProgressInitializationFailed";
    public static final String MODULE_COMPLETED = "ModuleCompleted";
    public static final String ASSESSMENT_COMPLETED = "AssessmentCompleted";
    public static final String COURSE_COMPLETED = "CourseCompleted";

    private ProgressEventTypes() { }
}
