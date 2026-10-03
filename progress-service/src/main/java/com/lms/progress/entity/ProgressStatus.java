package com.lms.progress.entity;

public enum ProgressStatus {
    /** Progress initialised, nothing done yet. */
    NOT_STARTED,
    /** At least one module or assessment attempt, modules not yet all completed. */
    IN_PROGRESS,
    /** All modules completed (100 %) but no passed assessment yet. */
    AWAITING_ASSESSMENT,
    /** Decision rule fired: 100 % modules AND assessment passed. */
    COMPLETED,
    /** Progress could not be initialised (saga compensated with EnrollmentFailed). */
    FAILED
}
